package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.weihe.schedule.model.Course;

/**
 * The teachers and venues already used in a term, offered as shortcuts.
 *
 * <p>A form asks for the same handful of names over and over -- the teacher who
 * takes three courses, the building every lab is in -- and typing them out each
 * time is the slowest part of adding a course. Everything here is derived from
 * the courses that already exist, so the form only ever offers something the user
 * has actually written before. Nothing is invented, and nothing is remembered
 * separately: there is no usage log, and this is not a history of what was
 * tapped. It is a count of what is in the timetable.</p>
 *
 * <h2>What counts as the same value</h2>
 *
 * <p>Values are compared exactly, and a value that is empty or only spaces is not
 * a suggestion at all. The text that comes back is the text that was stored, byte
 * for byte: a teacher's name is the user's to spell, and normalising it here would
 * put a name into the field that the timetable has never contained.</p>
 *
 * <h2>Ordering</h2>
 *
 * <p>Most used first. Ties are broken by the natural order of the text rather than
 * by which course happened to come first, so the same timetable produces the same
 * row every time whatever order the courses are read in -- a shortcut that
 * reshuffles itself between visits is one nobody learns.</p>
 */
public final class CourseSuggestions {

    /**
     * How many shortcuts to offer.
     *
     * <p>Enough to cover a term's usual handful, few enough that the form does not
     * turn into a list of everything. The value the user wants is almost always in
     * the first few, and past that the field is right there.</p>
     */
    public static final int DEFAULT_LIMIT = 6;

    private CourseSuggestions() {
    }

    /** The most used teachers in these courses, most used first. */
    public static List<String> teachers(List<Course> courses) {
        return teachers(courses, DEFAULT_LIMIT);
    }

    /** The most used teachers, at most {@code limit} of them. */
    public static List<String> teachers(List<Course> courses, int limit) {
        return rank(courses, TEACHER, limit);
    }

    /** The most used venues in these courses, most used first. */
    public static List<String> locations(List<Course> courses) {
        return locations(courses, DEFAULT_LIMIT);
    }

    /** The most used venues, at most {@code limit} of them. */
    public static List<String> locations(List<Course> courses, int limit) {
        return rank(courses, LOCATION, limit);
    }

    // ------------------------------------------------------------- internals

    /** Which field of a course is being counted. */
    private interface Field {
        String of(Course course);
    }

    private static final Field TEACHER = course -> course.teacher;
    private static final Field LOCATION = course -> course.location;

    /**
     * Counts the distinct values of one field and returns the most used ones.
     *
     * <p>Neither the list nor the courses in it are touched: the counting happens
     * in a map of its own and the result holds the same strings that were read.</p>
     */
    private static List<String> rank(List<Course> courses, Field field, int limit) {
        List<String> ranked = new ArrayList<>();
        if (courses == null || limit <= 0) {
            return ranked;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Course course : courses) {
            if (course == null) {
                continue;
            }
            String value = field.of(course);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            Integer seen = counts.get(value);
            counts.put(value, seen == null ? 1 : seen + 1);
        }
        ranked.addAll(counts.keySet());
        Collections.sort(ranked, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int byUse = counts.get(right).intValue() - counts.get(left).intValue();
                // The text decides a tie, not the order the courses arrived in.
                return byUse != 0 ? byUse : left.compareTo(right);
            }
        });
        return ranked.size() > limit
                ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
    }
}
