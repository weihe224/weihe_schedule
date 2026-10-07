package com.weihe.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The week grid as data: for every weekday, the vertical runs of courses and
 * where each course sits inside them.
 *
 * <p>This is the rule the grid draws with, extracted so it can be tested on the
 * JVM. It exists because two rendering bugs slipped past every check: a course
 * starting inside another one was skipped entirely, and later the renderer built
 * course views without ever attaching them, leaving the grid blank while all
 * tests passed. Returning a structure the screen merely walks means a test can
 * assert that every course landed somewhere.</p>
 *
 * <p>Two levels, and the difference matters:</p>
 *
 * <ul>
 *   <li>a <b>cluster</b> is a run of courses that touch in time. Its lane count
 *       is shared by every cell inside it, because courses that overlap across
 *       different start periods have to line up in the same columns;</li>
 *   <li>a <b>cell</b> is one start period inside that cluster. It carries the
 *       courses starting there and the period range it covers.</li>
 * </ul>
 *
 * <p>A day column is therefore one frame per cluster, with each cell positioned
 * by its start period. Courses that run past a cluster's last start period keep
 * their own height, which is why a two-period course drawn next to a
 * four-period one stays two periods tall.</p>
 */
public final class WeekGridPlan {

    /**
     * A run of courses that touch in time, drawn as one day column block.
     *
     * <p>All cells in a cluster report the same {@link #laneCount()}, so their
     * lanes line up vertically.</p>
     */
    public static final class Cluster {
        private final int day;
        private final int startPeriod;
        private final int endPeriod;
        private final int laneCount;
        private final List<Cell> cells;

        Cluster(int day, int startPeriod, int endPeriod, int laneCount,
                List<Cell> cells) {
            this.day = day;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
            this.laneCount = laneCount;
            this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
        }

        /** Weekday, 1 = Monday. */
        public int day() {
            return day;
        }

        /** First period covered by any course in this cluster. */
        public int startPeriod() {
            return startPeriod;
        }

        /** Last period covered by any course in this cluster. */
        public int endPeriod() {
            return endPeriod;
        }

        /** How many period rows the cluster spans; never below 1. */
        public int periodSpan() {
            return Math.max(1, endPeriod - startPeriod + 1);
        }

        /** Lanes every cell in this cluster shares; never below 1. */
        public int laneCount() {
            return laneCount;
        }

        /** The cells, ordered by start period. */
        public List<Cell> cells() {
            return cells;
        }

        /** Every course in the cluster, in cell then lane order. */
        public List<Block> blocks() {
            List<Block> blocks = new ArrayList<>();
            for (Cell cell : cells) {
                blocks.addAll(cell.blocks());
            }
            return blocks;
        }

        public String describe() {
            return "d" + day + " p" + startPeriod + "-" + endPeriod
                    + " x" + laneCount + " " + cells;
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    /** One start period inside a cluster. */
    public static final class Cell {
        private final int day;
        private final int startPeriod;
        private final int endPeriod;
        private final int laneCount;
        private final List<Block> blocks;

        Cell(int day, int startPeriod, int endPeriod, int laneCount,
                List<Block> blocks) {
            this.day = day;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
            this.laneCount = laneCount;
            this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        }

        /** Weekday, 1 = Monday. */
        public int day() {
            return day;
        }

        /** The period every course in this cell starts at. */
        public int startPeriod() {
            return startPeriod;
        }

        /** Last period covered, counting the longest course in this cell. */
        public int endPeriod() {
            return endPeriod;
        }

        /** How many period rows this cell spans; never below 1. */
        public int periodSpan() {
            return Math.max(1, endPeriod - startPeriod + 1);
        }

        /** Lanes the enclosing cluster uses. */
        public int laneCount() {
            return laneCount;
        }

        /** The courses starting here, ordered by lane. */
        public List<Block> blocks() {
            return blocks;
        }

        public String describe() {
            StringBuilder text = new StringBuilder("d" + day + " p" + startPeriod
                    + "-" + endPeriod + " [");
            for (int i = 0; i < blocks.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(blocks.get(i).course().name)
                        .append("@lane").append(blocks.get(i).lane());
            }
            return text.append(']').toString();
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    /** One course inside a cell. */
    public static final class Block {
        private final Course course;
        private final int day;
        private final int startPeriod;
        private final int endPeriod;
        private final int lane;
        private final int laneCount;

        Block(Course course, int day, int startPeriod, int endPeriod,
                int lane, int laneCount) {
            this.course = course;
            this.day = day;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
            this.lane = lane;
            this.laneCount = laneCount;
        }

        public Course course() {
            return course;
        }

        /** Weekday, 1 = Monday. */
        public int day() {
            return day;
        }

        /** First period the block covers. */
        public int startPeriod() {
            return startPeriod;
        }

        /** Last period the block covers. */
        public int endPeriod() {
            return endPeriod;
        }

        /** How many periods tall the block is; never below 1. */
        public int periodSpan() {
            return Math.max(1, endPeriod - startPeriod + 1);
        }

        /** Position inside the cluster, 0-based, counted from the left. */
        public int lane() {
            return lane;
        }

        /** How many lanes the cluster uses; never below 1. */
        public int laneCount() {
            return laneCount;
        }

        /** True when this block has to share its weekday column with others. */
        public boolean sharesColumn() {
            return laneCount > 1;
        }

        @Override
        public String toString() {
            return course.name + " d" + day + " p" + startPeriod + "-" + endPeriod
                    + " lane " + lane + "/" + laneCount;
        }
    }

    private static final Comparator<Course> BY_START_THEN_END =
            Comparator.comparingInt((Course course) -> course.startPeriod)
                    .thenComparingInt(course -> course.endPeriod)
                    .thenComparing(course -> course.id);

    private WeekGridPlan() {
    }

    /**
     * Every cluster of {@code courses}, ordered by weekday and then by start
     * period.
     *
     * @param courses courses of a single week, as returned by
     *                {@link CourseQuery#inWeek(List, int)}
     */
    public static List<Cluster> plan(List<Course> courses) {
        List<Cluster> clusters = new ArrayList<>();
        if (courses == null || courses.isEmpty()) {
            return clusters;
        }
        for (int day = 1; day <= 7; day++) {
            List<Course> ofDay = new ArrayList<>();
            for (Course course : courses) {
                if (course.dayOfWeek == day) {
                    ofDay.add(course);
                }
            }
            if (ofDay.isEmpty()) {
                continue;
            }
            ofDay.sort(BY_START_THEN_END);
            clusters.addAll(clustersOfDay(ofDay, day));
        }
        return clusters;
    }

    /** Every course of the plan, flattened, in drawing order. */
    public static List<Block> blocks(List<Course> courses) {
        List<Block> blocks = new ArrayList<>();
        for (Cluster cluster : plan(courses)) {
            blocks.addAll(cluster.blocks());
        }
        return blocks;
    }

    /** Every cell of the plan, flattened, in drawing order. */
    public static List<Cell> cells(List<Course> courses) {
        List<Cell> cells = new ArrayList<>();
        for (Cluster cluster : plan(courses)) {
            cells.addAll(cluster.cells());
        }
        return cells;
    }

    /**
     * The clusters of one weekday.
     *
     * <p>Walking in start order, the next course joins the current cluster while
     * it starts no later than the furthest period already covered. Clusters are
     * independent -- nothing outside one can overlap anything inside it -- so
     * lane counts stay local and a quiet afternoon does not inherit the width of
     * a busy morning.</p>
     */
    private static List<Cluster> clustersOfDay(List<Course> ofDay, int day) {
        List<Cluster> clusters = new ArrayList<>();
        int index = 0;
        while (index < ofDay.size()) {
            int clusterEnd = index;
            int reached = ofDay.get(index).endPeriod;
            while (clusterEnd + 1 < ofDay.size()
                    && ofDay.get(clusterEnd + 1).startPeriod <= reached) {
                clusterEnd++;
                reached = Math.max(reached, ofDay.get(clusterEnd).endPeriod);
            }
            clusters.add(clusterOf(ofDay.subList(index, clusterEnd + 1), day, reached));
            index = clusterEnd + 1;
        }
        return clusters;
    }

    /**
     * Assigns lanes inside one cluster, then groups the courses into cells.
     *
     * <p>A course takes the first lane whose last course ends before it starts,
     * and a new lane when none is free. Lanes are handed out in start order, so
     * this is sequential greedy packing rather than optimal: it can need one
     * lane more than strictly necessary, which is the price of an implementation
     * simple enough to be tested.</p>
     */
    private static Cluster clusterOf(List<Course> cluster, int day, int endPeriod) {
        List<Integer> laneEnds = new ArrayList<>();
        List<Integer> laneOfCourse = new ArrayList<>();
        for (Course course : cluster) {
            int chosen = -1;
            for (int lane = 0; lane < laneEnds.size(); lane++) {
                if (laneEnds.get(lane) < course.startPeriod) {
                    chosen = lane;
                    break;
                }
            }
            if (chosen < 0) {
                laneEnds.add(0);
                chosen = laneEnds.size() - 1;
            }
            laneEnds.set(chosen, Math.max(laneEnds.get(chosen), course.endPeriod));
            laneOfCourse.add(chosen);
        }
        int laneCount = Math.max(1, laneEnds.size());

        // One cell per distinct start period, in ascending order.
        List<Cell> cells = new ArrayList<>();
        int index = 0;
        while (index < cluster.size()) {
            int startPeriod = cluster.get(index).startPeriod;
            List<Block> blocks = new ArrayList<>();
            int cellEnd = startPeriod;
            while (index < cluster.size()
                    && cluster.get(index).startPeriod == startPeriod) {
                Course course = cluster.get(index);
                blocks.add(new Block(course, day, course.startPeriod,
                        course.endPeriod, laneOfCourse.get(index), laneCount));
                cellEnd = Math.max(cellEnd, course.endPeriod);
                index++;
            }
            blocks.sort(Comparator.comparingInt(Block::lane));
            cells.add(new Cell(day, startPeriod, cellEnd, laneCount, blocks));
        }
        return new Cluster(day, cluster.get(0).startPeriod, endPeriod, laneCount, cells);
    }

    /** The clusters of one weekday, for callers that draw a single day. */
    public static List<Cluster> onDay(List<Course> weekCourses, int day) {
        List<Cluster> clusters = new ArrayList<>();
        for (Cluster cluster : plan(weekCourses)) {
            if (cluster.day() == day) {
                clusters.add(cluster);
            }
        }
        return Collections.unmodifiableList(clusters);
    }

    /** How many lanes the busiest weekday column needs. */
    public static int maxLaneCount(List<Course> courses) {
        int worst = 1;
        for (Cluster cluster : plan(courses)) {
            worst = Math.max(worst, cluster.laneCount());
        }
        return worst;
    }
}
