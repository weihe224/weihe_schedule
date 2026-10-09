package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * Tests for how a week is laid out on the grid.
 *
 * <p>These exist because of two failures the compiler could not catch. The first
 * was a course starting inside another one being skipped entirely. The second
 * was the renderer building course views and never attaching them, which left
 * the whole overview blank while every test still passed.</p>
 *
 * <p>So the rule now returns a structure, and the checks below are written
 * against it: every course must appear exactly once, cells of one cluster must
 * agree on their lane count, and no two courses in the same lane may overlap in
 * time.</p>
 */
public class WeekGridPlanTest {

    private static Course course(long id, int day, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.id = id;
        course.name = "\u8BFE" + id;
        course.dayOfWeek = day;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        return course;
    }

    private static WeekGridPlan.Block blockOf(List<Course> week, long id) {
        for (WeekGridPlan.Block block : WeekGridPlan.blocks(week)) {
            if (block.course().id == id) {
                return block;
            }
        }
        throw new AssertionError("no block for course " + id);
    }

    private static WeekGridPlan.Cell cellAt(List<Course> week, int day, int period) {
        for (WeekGridPlan.Cell cell : WeekGridPlan.cells(week)) {
            if (cell.day() == day && cell.startPeriod() == period) {
                return cell;
            }
        }
        throw new AssertionError("no cell for day " + day + " period " + period);
    }

    @Test
    public void everyCourseLandsInExactlyOneCell() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 2), course(2, 1, 2, 3), course(3, 1, 5, 6),
                course(4, 2, 1, 4), course(5, 5, 3, 3));

        Set<Long> drawn = new HashSet<>();
        for (WeekGridPlan.Cluster cluster : WeekGridPlan.plan(week)) {
            int covered = 0;
            for (WeekGridPlan.Cell cell : cluster.cells()) {
                assertEquals(cluster.laneCount(), cell.laneCount());
                assertTrue(cell.startPeriod() >= cluster.startPeriod());
                assertTrue(cell.endPeriod() <= cluster.endPeriod());
                covered = Math.max(covered, cell.periodSpan());
                for (WeekGridPlan.Block block : cell.blocks()) {
                    assertTrue("course drawn twice: " + block.course().id,
                            drawn.add(Long.valueOf(block.course().id)));
                    assertEquals(cell.day(), block.day());
                    assertEquals(cell.startPeriod(), block.startPeriod());
                    assertEquals(cell.laneCount(), block.laneCount());
                    assertTrue(block.endPeriod() <= cell.endPeriod());
                    assertTrue(block.lane() >= 0);
                    assertTrue(block.lane() < cluster.laneCount());
                }
            }
            assertTrue(covered >= 1);
        }
        assertEquals(week.size(), drawn.size());
    }

    @Test
    public void noTwoCoursesInTheSameLaneOverlapInTime() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 2), course(2, 1, 2, 3), course(3, 1, 2, 4),
                course(4, 1, 5, 6));

        for (WeekGridPlan.Cluster cluster : WeekGridPlan.plan(week)) {
            List<WeekGridPlan.Block> blocks = cluster.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                for (int j = i + 1; j < blocks.size(); j++) {
                    WeekGridPlan.Block first = blocks.get(i);
                    WeekGridPlan.Block second = blocks.get(j);
                    if (first.lane() != second.lane()) {
                        continue;
                    }
                    assertTrue("lane " + first.lane() + " holds overlapping "
                                    + first.course().name + " and "
                                    + second.course().name + " in " + cluster,
                            first.startPeriod() > second.endPeriod()
                                    || second.startPeriod() > first.endPeriod());
                }
            }
        }
    }

    @Test
    public void aCourseStartingInsideAnotherOneKeepsItsOwnStartPeriod() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 2), course(2, 1, 2, 3));

        assertEquals(2, WeekGridPlan.cells(week).size());
        assertEquals(1, blockOf(week, 1).startPeriod());
        assertEquals(2, blockOf(week, 1).endPeriod());
        assertEquals(2, blockOf(week, 2).startPeriod());
        assertEquals(3, blockOf(week, 2).endPeriod());
        assertEquals(2, blockOf(week, 1).periodSpan());
        assertEquals(2, blockOf(week, 2).periodSpan());
        assertTrue(blockOf(week, 1).sharesColumn());
        assertTrue(blockOf(week, 2).sharesColumn());
    }

    @Test
    public void aClusterSpansFromItsFirstStartToItsLastEnd() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 2), course(2, 1, 2, 5));

        WeekGridPlan.Cluster cluster = WeekGridPlan.plan(week).get(0);

        assertEquals(1, cluster.startPeriod());
        assertEquals(5, cluster.endPeriod());
        assertEquals(5, cluster.periodSpan());
        assertEquals(2, cluster.cells().size());
        // Both cells share the cluster's lanes, so the courses line up.
        assertEquals(cluster.laneCount(), cluster.cells().get(0).laneCount());
        assertEquals(cluster.laneCount(), cluster.cells().get(1).laneCount());
        assertEquals(cluster.laneCount(), 2);
    }

    @Test
    public void aShortCourseKeepsItsOwnHeightInsideATallCluster() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 4), course(2, 1, 2, 2));

        // The long course is four periods tall, the short one is one: neither is
        // stretched to the other's height.
        assertEquals(4, blockOf(week, 1).periodSpan());
        assertEquals(1, blockOf(week, 2).periodSpan());
        // The cell of the long course ends where it ends; the short course gets
        // its own one-period cell inside the same cluster.
        assertEquals(4, cellAt(week, 1, 1).periodSpan());
        assertEquals(1, cellAt(week, 1, 2).periodSpan());
        assertTrue(blockOf(week, 1).lane() != blockOf(week, 2).lane());
    }

    @Test
    public void coursesSharingTheSameStartPeriodEachGetALane() {
        List<Course> week = Arrays.asList(
                course(1, 3, 5, 6), course(2, 3, 5, 6), course(3, 3, 5, 6));

        WeekGridPlan.Cell cell = cellAt(week, 3, 5);

        assertEquals(3, cell.laneCount());
        assertEquals(3, cell.blocks().size());
        List<Integer> lanes = new ArrayList<>();
        for (WeekGridPlan.Block block : cell.blocks()) {
            lanes.add(block.lane());
        }
        assertEquals(Arrays.asList(0, 1, 2), lanes);
    }

    @Test
    public void aCourseMayReuseALaneOnceTheEarlierOneHasEnded() {
        List<Course> week = Arrays.asList(
                course(1, 2, 1, 2), course(2, 2, 3, 4));

        assertEquals(1, blockOf(week, 1).laneCount());
        assertEquals(1, blockOf(week, 2).laneCount());
        assertFalse(blockOf(week, 1).sharesColumn());
        assertEquals(0, blockOf(week, 2).lane());
    }

    @Test
    public void separateClustersOnOneDayDoNotShareLanes() {
        List<Course> week = Arrays.asList(
                course(1, 4, 1, 2), course(2, 4, 3, 4));

        assertEquals(1, blockOf(week, 1).laneCount());
        assertEquals(1, blockOf(week, 2).laneCount());
        assertEquals(2, WeekGridPlan.plan(week).size());
    }

    @Test
    public void weekdaysNeverShareLanes() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 2), course(2, 2, 1, 2));

        assertEquals(1, blockOf(week, 1).laneCount());
        assertEquals(1, blockOf(week, 2).laneCount());
    }

    @Test
    public void clustersComeBackOrderedByDayThenPeriod() {
        List<Course> week = Arrays.asList(
                course(1, 3, 5, 6), course(2, 1, 7, 8), course(3, 1, 1, 2),
                course(4, 2, 1, 1));

        List<String> order = new ArrayList<>();
        for (WeekGridPlan.Cluster cluster : WeekGridPlan.plan(week)) {
            order.add(cluster.day() + ":" + cluster.startPeriod());
        }
        assertEquals(Arrays.asList("1:1", "1:7", "2:1", "3:5"), order);
    }

    @Test
    public void periodSpanIsNeverBelowOne() {
        Course broken = course(1, 1, 4, 2);
        assertEquals(1, blockOf(Arrays.asList(broken), 1).periodSpan());
        assertEquals(2, blockOf(Arrays.asList(course(2, 1, 3, 4)), 2).periodSpan());
    }

    @Test
    public void aChainOfOverlapsSharesTheNarrowestPossibleColumns() {
        // 1-4 and 5-6 touch 2-2, but 2-2 is the only thing they have in common,
        // so 1-4 and 5-6 are not connected to each other. Each pair forms its
        // own cluster and gets its own narrow lane count.
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 4), course(2, 1, 5, 6), course(3, 1, 2, 2));

        assertEquals(2, blockOf(week, 1).laneCount());
        assertEquals(1, blockOf(week, 2).laneCount());
        assertEquals(2, blockOf(week, 3).laneCount());
        assertEquals(0, blockOf(week, 1).lane());
        assertEquals(0, blockOf(week, 2).lane());
        assertEquals(1, blockOf(week, 3).lane());
    }

    @Test
    public void aClusterIsAsWideAsItsWidestOverlapChain() {
        // 4-6 touches 2-2 only through 1-5, but overlapping is transitive along
        // that chain, so all three share one cluster and one lane count.
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 5), course(2, 1, 2, 2), course(3, 1, 4, 6));

        assertEquals(2, blockOf(week, 1).laneCount());
        assertEquals(2, blockOf(week, 2).laneCount());
        assertEquals(2, blockOf(week, 3).laneCount());
        assertEquals(0, blockOf(week, 1).lane());
        assertEquals(1, blockOf(week, 2).lane());
        assertEquals(1, blockOf(week, 3).lane());
    }

    @Test
    public void anEmptyWeekHasNoClusters() {
        assertTrue(WeekGridPlan.plan(new ArrayList<Course>()).isEmpty());
        assertTrue(WeekGridPlan.plan(null).isEmpty());
        assertTrue(WeekGridPlan.cells(null).isEmpty());
        assertEquals(1, WeekGridPlan.maxLaneCount(new ArrayList<Course>()));
    }

    @Test
    public void onDayKeepsOnlyThatWeekday() {
        List<Course> week = Arrays.asList(course(1, 1, 1, 2), course(2, 2, 1, 2));

        List<WeekGridPlan.Cluster> monday = WeekGridPlan.onDay(week, 1);

        assertEquals(1, monday.size());
        assertEquals(1, monday.get(0).day());
    }

    @Test
    public void maxLaneCountReportsTheBusiestColumn() {
        List<Course> week = Arrays.asList(
                course(1, 1, 1, 2), course(2, 1, 2, 3), course(3, 2, 1, 2));

        assertEquals(2, WeekGridPlan.maxLaneCount(week));
    }
}
