package com.weihe.schedule.reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AlarmRegistrationTest {
    private static ReminderSchedule.Entry entry(int slot, long course) {
        return new ReminderSchedule.Entry(slot, course, 20000L, course * 1000L);
    }

    private static final class FakeStore implements AlarmRegistration.Store {
        final Map<Integer, ReminderSchedule.Entry> alarms = new LinkedHashMap<>();
        final Map<Integer, ReminderSchedule.Entry> payloads = new LinkedHashMap<>();
        List<ReminderSchedule.Entry> ledger;
        int refusedSlot = -1;
        int refusedCancel = -1;
        final RuntimeException failure = new IllegalStateException("refused");

        FakeStore(List<ReminderSchedule.Entry> old) {
            ledger = new ArrayList<>(old);
            for (ReminderSchedule.Entry entry : old) {
                alarms.put(entry.slot(), entry);
                payloads.put(entry.slot(), entry);
            }
        }

        public void set(ReminderSchedule.Entry entry) {
            payloads.put(entry.slot(), entry);
            if (entry.slot() == refusedSlot) {
                throw failure;
            }
            alarms.put(entry.slot(), entry);
        }

        public void restorePayload(ReminderSchedule.Entry entry) {
            payloads.put(entry.slot(), entry);
        }

        public void cancel(int slot) {
            if (slot == refusedCancel) {
                throw failure;
            }
            alarms.remove(slot);
            payloads.remove(slot);
        }

        public void save(List<ReminderSchedule.Entry> entries) {
            ledger = new ArrayList<>(entries);
        }
    }

    @Test
    public void completeReplacementRemovesOnlyObsoleteAlarms() {
        var old = List.of(entry(1000, 1), entry(1001, 2));
        var planned = List.of(entry(1000, 3));
        var store = new FakeStore(old);
        assertEquals(1, AlarmRegistration.replace(old, planned, store));
        assertEquals(planned, store.ledger);
        assertEquals(planned, new ArrayList<>(store.alarms.values()));
    }

    @Test
    public void firstRegistrationFailureKeepsTheEntireOldPlan() {
        var old = List.of(entry(1000, 1), entry(1001, 2));
        var store = new FakeStore(old);
        store.refusedSlot = 1000;
        assertSame(store.failure, assertThrows(IllegalStateException.class,
                () -> AlarmRegistration.replace(old, List.of(entry(1000, 3)), store)));
        assertEquals(old, store.ledger);
        assertEquals(old, new ArrayList<>(store.alarms.values()));
        assertEquals(old, new ArrayList<>(store.payloads.values()));
    }

    @Test
    public void partialFailureKeepsSuccessfulNewAndUntouchedOldAlarms() {
        var old = List.of(entry(1000, 1), entry(1001, 2), entry(1002, 3));
        var planned = List.of(entry(1000, 4), entry(1001, 5));
        var store = new FakeStore(old);
        store.refusedSlot = 1001;
        assertThrows(IllegalStateException.class,
                () -> AlarmRegistration.replace(old, planned, store));
        assertEquals(entry(1000, 4), store.alarms.get(1000));
        assertEquals(entry(1001, 2), store.payloads.get(1001));
        assertEquals(entry(1002, 3), store.alarms.get(1002));
        assertEquals(3, store.ledger.size());
        for (var entry : store.ledger) {
            assertEquals(entry, store.alarms.get(entry.slot()));
            assertEquals(entry, store.payloads.get(entry.slot()));
        }
    }

    @Test
    public void retryConvergesAfterPartialFailure() {
        var old = List.of(entry(1000, 1), entry(1001, 2), entry(1002, 3));
        var planned = List.of(entry(1000, 4), entry(1001, 5));
        var store = new FakeStore(old);
        store.refusedSlot = 1001;
        assertThrows(IllegalStateException.class,
                () -> AlarmRegistration.replace(old, planned, store));
        store.refusedSlot = -1;
        assertEquals(2, AlarmRegistration.replace(store.ledger, planned, store));
        assertEquals(planned, store.ledger);
        assertEquals(planned, new ArrayList<>(store.alarms.values()));
    }

    @Test
    public void cancellationFailureDoesNotLoseTrackOfSurvivingAlarms() {
        var old = List.of(entry(1000, 1), entry(1001, 2), entry(1002, 3));
        var store = new FakeStore(old);
        store.refusedCancel = 1002;
        assertThrows(IllegalStateException.class,
                () -> AlarmRegistration.replace(old, List.of(entry(1000, 4)), store));
        assertNull(store.alarms.get(1001));
        assertEquals(List.of(entry(1002, 3), entry(1000, 4)), store.ledger);
    }

    @Test
    public void emptyPlanCancelsAllAlarms() {
        var old = List.of(entry(1000, 1), entry(1001, 2));
        var store = new FakeStore(old);
        assertEquals(0, AlarmRegistration.replace(old, List.of(), store));
        assertTrue(store.alarms.isEmpty());
        assertTrue(store.ledger.isEmpty());
    }
}
