import java.util.*;
import java.util.concurrent.locks.*;

public class Main {
    static class Crossroad {
        private final Lock mutex = new ReentrantLock();
        private final Condition accessCondition = mutex.newCondition();
        private boolean occupied = false;

        private final Map<Integer, Queue<Vehicle>> waitingLines = new HashMap<>();
        private final Set<Integer> priorityDirections = new HashSet<>(Arrays.asList(1, 3)); // головні напрямки

        public Crossroad() {
            for (int i = 1; i <= 4; i++) {
                waitingLines.put(i, new LinkedList<>());
            }
        }

        public boolean isPriority(int dir) {
            return priorityDirections.contains(dir);
        }

        public void showCrossroadInfo() {
            mutex.lock();
            try {
                Map<Integer, String> directionIcons = Map.of(
                        1, "↑", 2, "↓", 3, "←", 4, "→"
                );

                StringBuilder primaryRoad = new StringBuilder();
                StringBuilder secondaryRoad = new StringBuilder();

                for (int i = 1; i <= 4; i++) {
                    String label = switch (i) {
                        case 1 -> "1 (↑ Пн)";
                        case 2 -> "2 (↓ Пд)";
                        case 3 -> "3 (← Зх)";
                        case 4 -> "4 (→ Сх)";
                        default -> i + " (?)";
                    };
                    if (priorityDirections.contains(i)) {
                        primaryRoad.append(label).append("  ");
                    } else {
                        secondaryRoad.append(label).append("  ");
                    }
                }

                System.out.println("""
                ================================
                ІМІТАЦІЯ ДОРОЖНЬОГО РУХУ
                Основні напрямки:    %s
                Другорядні напрямки: %s
                ================================
                """.formatted(primaryRoad.toString().trim(), secondaryRoad.toString().trim()));
            } finally {
                mutex.unlock();
            }
        }

        public void requestEntry(Vehicle v) throws InterruptedException {
            mutex.lock();
            try {
                waitingLines.get(v.origin).add(v);
                while (true) {
                    if (waitingLines.get(v.origin).peek() != v || occupied || conflictExists(v)) {
                        accessCondition.await();
                    } else {
                        waitingLines.get(v.origin).poll();
                        occupied = true;
                        System.out.printf(" Машина #%d В'ЇХАЛА на перехрестя: %s → %s (%s напрямок)\n",
                                v.id,
                                directionName(v.origin),
                                directionName(v.target),
                                roadLabel(v.origin));
                        return;
                    }
                }
            } finally {
                mutex.unlock();
            }
        }

        public void releaseEntry(Vehicle v) {
            mutex.lock();
            try {
                occupied = false;
                System.out.printf(" Машина #%d ПРОЇХАЛА через перехрестя: %s → %s (%s напрямок)\n",
                        v.id,
                        directionName(v.origin),
                        directionName(v.target),
                        roadLabel(v.origin));
                accessCondition.signalAll();
            } finally {
                mutex.unlock();
            }
        }

        private boolean conflictExists(Vehicle v) {
            if (!isPriority(v.origin)) {
                for (int d : priorityDirections) {
                    if (!waitingLines.get(d).isEmpty()) return true;
                }
            }

            for (int d = 1; d <= 4; d++) {
                if (d == v.origin || waitingLines.get(d).isEmpty()) continue;
                Vehicle other = waitingLines.get(d).peek();
                boolean equalPriority = isPriority(d) == isPriority(v.origin);
                boolean blocksRight = equalPriority && isToRight(v.origin, d);
                if (blocksRight) return true;
            }
            return false;
        }

        private boolean isToRight(int current, int other) {
            return switch (current) {
                case 1 -> other == 3;
                case 3 -> other == 2;
                case 2 -> other == 4;
                case 4 -> other == 1;
                default -> false;
            };
        }

        private String roadLabel(int dir) {
            return isPriority(dir) ? "основний" : "другорядний";
        }

        private String directionName(int dir) {
            return switch (dir) {
                case 1 -> "↑ Північ";
                case 2 -> "↓ Південь";
                case 3 -> "→ Схід";
                case 4 -> "← Захід";
                default -> "???";
            };
        }
    }

    static class Vehicle extends Thread {
        private static final int DRIVE_DURATION_MS = 4000;
        public final int id;
        public final int origin;
        public final int target;
        private final Crossroad crossroad;

        public Vehicle(int id, int origin, int target, Crossroad crossroad) {
            this.id = id;
            this.origin = origin;
            this.target = target;
            this.crossroad = crossroad;
        }

        @Override
        public void run() {
            try {
                System.out.printf(" Машина #%d прибуває з %s і планує рух до %s (%s напрямок)\n",
                        id,
                        crossroad.directionName(origin),
                        crossroad.directionName(target),
                        crossroad.roadLabel(origin));
                crossroad.requestEntry(this);
                Thread.sleep(DRIVE_DURATION_MS);
                crossroad.releaseEntry(this);
            } catch (InterruptedException e) {
                System.out.println(" Машина #" + id + " була зупинена.");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Crossroad crossroad = new Crossroad();
        crossroad.showCrossroadInfo();
        Random rnd = new Random();

        for (int i = 1; i <= 15; i++) {
            int origin = 1 + rnd.nextInt(4);
            int target;
            do {
                target = 1 + rnd.nextInt(4);
            } while (target == origin);

            Vehicle v = new Vehicle(i, origin, target, crossroad);
            v.start();
            Thread.sleep(rnd.nextInt(1500));
        }
    }
}
