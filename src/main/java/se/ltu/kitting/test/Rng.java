package se.ltu.kitting.test;

import java.util.*;

/**
 * Randomization utility.
 * @author Christoffer Fink
 */
@SuppressWarnings("serial")
public class Rng extends Random {

    public Rng(final long seed) {
        super(seed);
    }

    /** Uniformly random in [min, max[. Hence min == max is allowed. */
    public int nextInt(final int min, final int max) {
        return nextInt(max - min + 1) + min;
    }

    /** Uniformly random in [min, max[. Hence min == max is allowed. */
    public double nextDouble(final double min, final double max) {
        return min + (max - min)*nextDouble();
    }

    /** Generate {@code true} with the given probability. */
    public boolean nextBoolean(final double probability) {
        if (probability > 1.0 || probability < 0.0) {
            throw new IllegalArgumentException(probability + " not in [0,1]");
        }
        return nextDouble() < probability;
    }

    /**
     * Remove and return an element from the list.
     * Mutates the given {@code list}!
     */
    public <T> T removeRandomElement(final List<T> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Cannot remove an element from an empty list");
        }
        final int index = nextInt(list.size());
        return list.remove(index);
    }

    /** Return an element from the list. */
    public <T> T selectRandomElement(final List<T> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Cannot select an element from an empty list");
        }
        final int index = nextInt(list.size());
        return list.get(index);
    }

    /** Return an element from the collection. */
    public <T> T selectRandomElement(final Collection<T> col) {
        return selectRandomElement(new ArrayList<>(col));
    }

    /** Return a shuffled copy of the collection. */
    public <T> List<T> shuffle(final Collection<T> col) {
        final List<T> copy = new ArrayList<>(col);
        java.util.Collections.shuffle(copy, this);
        return copy;
    }

    /**
     * Like {@link #randomSubset(Collection, int)} but returns a list instead
     * of a set.
     */
    public <T> List<T> randomSublist(final Collection<T> col, final int size) {
        return shuffle(col).subList(0, size);
    }

    /**
     * If the collection is NOT a set AND contains duplicates, the size of the
     * returned set will be less than the given {@code size}.
     */
    public <T> Set<T> randomSubset(final Collection<T> col, final int size) {
        return new HashSet<>(randomSublist(col, size));
    }

}
