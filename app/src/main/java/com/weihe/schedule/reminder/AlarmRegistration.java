package com.weihe.schedule.reminder;

import java.util.ArrayList;
import java.util.List;

/** Keeps the ledger consistent when replacing alarms fails partway through. */
final class AlarmRegistration {

    interface Store {
        void set(ReminderSchedule.Entry entry);
        void restorePayload(ReminderSchedule.Entry entry);
        void cancel(int slot);
        void save(List<ReminderSchedule.Entry> entries);
    }

    private AlarmRegistration() {
    }

    static int replace(List<ReminderSchedule.Entry> previous,
            List<ReminderSchedule.Entry> planned, Store store) {
        List<ReminderSchedule.Entry> retained = new ArrayList<>(previous);
        try {
            for (ReminderSchedule.Entry entry : planned) {
                try {
                    store.set(entry);
                } catch (RuntimeException failed) {
                    // FLAG_UPDATE_CURRENT changes the old PendingIntent's extras
                    // before AlarmManager can refuse the new alarm. Put them back
                    // so a surviving old alarm still matches its ledger entry.
                    ReminderSchedule.Entry old = ReminderSchedule.findBySlot(
                            retained, entry.slot());
                    if (old != null) {
                        try {
                            store.restorePayload(old);
                        } catch (RuntimeException recoveryFailed) {
                            failed.addSuppressed(recoveryFailed);
                        }
                    }
                    throw failed;
                }
                retained.removeIf(old -> old.slot() == entry.slot());
                retained.add(entry);
            }
            // Destructive cleanup only follows a complete registration pass.
            for (ReminderSchedule.Entry old : previous) {
                if (ReminderSchedule.findBySlot(planned, old.slot()) == null) {
                    store.cancel(old.slot());
                    retained.removeIf(entry -> entry.slot() == old.slot());
                }
            }
        } catch (RuntimeException failed) {
            try {
                store.save(new ArrayList<>(retained));
            } catch (RuntimeException saveFailed) {
                failed.addSuppressed(saveFailed);
            }
            throw failed;
        }
        // Preserve the planner's order after a successful replacement.
        store.save(new ArrayList<>(planned));
        return planned.size();
    }
}
