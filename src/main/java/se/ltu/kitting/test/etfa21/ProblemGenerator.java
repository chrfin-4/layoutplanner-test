package se.ltu.kitting.test.etfa21;

import java.util.*;
import java.util.function.Predicate;
import se.ltu.kitting.model.*;
import se.ltu.kitting.score.HardScore;
import se.ltu.kitting.score.SoftScore;
import ch.rfin.util.Pair;
import se.ltu.kitting.test.Rng;

import static ch.rfin.util.Pair.pair;
import static se.ltu.kitting.model.Dimensions.dimensions;
import static se.ltu.kitting.model.Surface.surface;

/**
 * Randomly generate layout planning problems - ETFA21 version.
 * This is the version of ProblemGenerator used to generate problem instances
 * for the ETFA 2021 paper.
 * @see se.ltu.kitting.test.ProblemGenerator
 * @author Christoffer Fink
 */
public class ProblemGenerator {

    private static final List<Side> allSides = Arrays.asList(Side.values());
    private final Rng rng;
    private int minPartSide = 20;
    private int minSurfaceSide = 800;
    private int maxSurfaceSide = 1200;
    private int surfaceHeight = 500;
    /** Generate at least this many parts. */
    private int minParts = 10;
    /** Generate at most this many parts. */
    private int maxParts = 25;
    private int minSurfaces = 2;
    private int maxSurfaces = 3;
    private double minDensity = 0.6;
    private double maxDensity = 0.8;
    /** Allow at least this many sides. */
    private int minAllowedDown = 3;
    /** Allow at most this many sides. */
    private int maxAllowedDown = 6;

    /** Probability that a part has a layout hint. */
    private double hintProbability = 0.33;
    /** Probability that a hint is mandatory. */
    private double mandatoryHintProbability = 0.33; // out of hints
    /** Probability that a part has a preferred side. */
    private double preferredSideProbability = 0.5;
    /**
     * Probability that a preferred side is chosen at random (instead of the
     * current)?
     * NOTE: if the preferred side is chosen randomly, there's a risk that that
     * side won't be feasible and so an optimal solution becomes impossible.
     * If it doesn't match a hint, it's guaranteed that an optimal solution
     * does not exist. So to GUARANTEE that an optimal solution DOES exist,
     * this MUST be set to 0.
     */
    private double randomPreferredProbability = 0.0;

    public ProblemGenerator(final long seed) {
        this(new Rng(seed));
    }

    public ProblemGenerator(final Rng rng) {
        this.rng = rng;
    }

    /** Generate a solved layout. */
    public Layout randomSolvedLayout() {
        return randomLayout(true);
    }

    /** Generate an unsolved layout. */
    public Layout randomLayout() {
        return randomLayout(false);
    }

    public Layout randomLayout(final boolean solved) {
        final Wagon wagon = randomWagon();
        final double density = rng.nextDouble(minDensity, maxDensity);
        List<Part> parts = new ArrayList<>();
        int partCount = rng.nextInt(minParts, maxParts);
        int partsPerSurface = partCount / wagon.surfaces().size() + 1;
        int remainder = partCount % wagon.surfaces().size();
        for (final Surface surface : wagon.surfaces()) {
            // FIXME: this is dirty
            if (remainder == 0) {
                partsPerSurface--;
                remainder--;
            } else {
                remainder--;
            }
            final Surface tmp = shrinkToDensity(surface, density);
            parts.addAll(fillSurfaceNormally(tmp, partsPerSurface));
        }
        parts = addAllowedSides(parts);
        parts = shuffleParts(parts);
        parts = addHints(parts);
        parts = addPreferredSide(parts);
        final var layout = new Layout(wagon, parts);
        sanityCheck(layout);
        if (!solved) {
            parts = resetParts(parts);
            layout.setParts(parts);
        }
        return layout;
    }

    public List<Part> resetParts(final List<Part> parts) {
        for (final Part part : parts) {
            part.setSideDown(null);
            part.setRotation(null);
            part.setPosition(null);
        }
        return rng.shuffle(parts);
    }

    public List<Part> addAllowedSides(List<Part> parts) {
        for (final Part part : parts) {
            final int sides = rng.nextInt(minAllowedDown, maxAllowedDown-1);
            final var tmp = rng.randomSublist(allSides, sides);
            tmp.add(part.getSideDown());
            part.setAllowedDown(tmp);
        }
        return parts;
    }

    /** Shuffles the list of parts AND also their orientations. */
    public List<Part> shuffleParts(final List<Part> parts) {
        for (final Part part : parts) {
            shufflePart(part);
        }
        return rng.shuffle(parts);
    }

    public Rotation randomRotation() {
        return rng.nextBoolean()
            ? Rotation.Z90
            : Rotation.ZERO;
    }

    /**
     * Randomly "relable" the sides of the part by placing it on a random
     * side (chosen from its allowed sides), randomly rotating it, and
     * changing its base dimensions so the transformations cancel out.
     * In other words, we want
     * {@code part.currentDimensions() == relabelSides(part).currentDimensions()}
     */
    public Part shufflePart(final Part part) {
        // Choose a random orientation.
        final var rotation = randomRotation();
        final Side side = randomAllowedSide(part);
        part.setRotation(rotation);
        part.setSideDown(side);
        return relabelSides(part);
    }

    public Side randomAllowedSide(final Part part) {
        return rng.selectRandomElement(part.getAllowedDown());
    }

    /**
     * Randomly "relable" the sides of the part by placing it on a random
     * side (chosen from its allowed sides), randomly rotating it, and
     * changing its base dimensions so the transformations cancel out.
     * In other words, we want
     * {@code part.currentDimensions() == relabelSides(part).currentDimensions()}
     */
    public Part relabelSides(final Part part) {
        final Dimensions currentSize = part.getSize();
        final var rotation = part.getRotation();
        final Side side = part.getSideDown();
        // Then perform the inverse rotation ...
        final var inverseRotation = inverse(rotation);
        final var beforeRotation = Rotation.rotateZeroOr90Z(inverseRotation, currentSize);
        // ... and then the inverse side flip.
        final var inverseSide = inverse(side);
        final var beforeSide = Rotation.rotateOntoSide(inverseSide, beforeRotation);
        // Now use this as the "original" dimensions.
        part.setSize(beforeSide);
        if (!part.currentDimensions().equals(currentSize)) {
            throw new AssertionError("Relabling failed! Dimensions don't match.");
        }
        return part;
    }

    public Rotation inverse(final Rotation rotation) {
        // As long as only 0 and 90 are allowed, 0 will "undo" 0, and 90 will undo 90.
        return rotation;
    }

    public static Side inverse(final Side side) {
        return side.opposite();
    }

    @Deprecated
    public Dimensions shuffleDimensions(final Dimensions dim) {
        List<Integer> dims = rng.shuffle(List.of(dim.x, dim.y, dim.z));
        return dimensions(dims.get(0), dims.get(1), dims.get(2));
    }

    /**
     * Give each part, with some probability, a layout hint based on
     * its current state.
     */
    public List<Part> addHints(List<Part> parts) {
        for (final Part part : parts) {
            if (rng.nextBoolean(hintProbability)) {
                addHint(part);
            }
        }
        return parts;
    }

    public Part addHint(Part part) {
        final var centerPos = part.currentCenter();
        final var surface = part.getPosition().z;
        final var hint = LayoutHint.hint(centerPos, surface)
            .withRotation(part.getRotation())
            .withSide(part.getSideDown());
        if (rng.nextBoolean(mandatoryHintProbability)) {
            part.setHint(hint.withWeight(LayoutHint.mandatoryWeight));
        } else {
            final var min = LayoutHint.defaultWeight;
            final var max = LayoutHint.mandatoryWeight; // exclusive
            part.setHint(hint.withWeight(rng.nextInt(min, max)));
        }
        return part;
    }

    public List<Part> addPreferredSide(List<Part> parts) {
        for (final Part part : parts) {
            if (rng.nextBoolean(preferredSideProbability)) {
                addPreferredSide(part);
            }
        }
        return parts;
    }

    public Part addPreferredSide(Part part) {
        if (rng.nextBoolean(randomPreferredProbability)) {
            part.setPreferredDown(randomAllowedSide(part));
        } else {
            part.setPreferredDown(part.getSideDown());
        }
        return part;
    }

    public <T> Predicate<T> withProbability(final double probability) {
        return t -> rng.nextBoolean(probability);
    }

    public Wagon randomWagon() {
        final int width = rng.nextInt(minSurfaceSide, maxSurfaceSide);
        final int depth = rng.nextInt(minSurfaceSide, maxSurfaceSide);
        final int height = surfaceHeight;
        final Dimensions size = dimensions(width, depth, height);
        final int surfaceCount = rng.nextInt(minSurfaces, maxSurfaces);
        final List<Surface> surfaces = new ArrayList<>();
        for (int i = 0; i < surfaceCount; i++) {
            final Dimensions origin = dimensions(0, 0, i*height);
            surfaces.add(surface(i, size, origin));
        }
        return Wagon.of("wagon", surfaces);
    }

    /*
     * Surface has area A. We want a new surface with area A' such that
     * A'/A = D. A = wd; A' = w'd' = (sw)(td), where s and t scale
     * width and depth w and d. So
     * A'/A = stwd/wd = st = D; s = D/t
     */
    public Surface shrinkToDensity(final Surface surface, final double density) {
        final double t = rng.nextDouble(density, 1);
        final double s = density/t;
        final Dimensions oldSize = surface.size();
        final Dimensions newSize = dimensions((int)(oldSize.x*s), (int)(oldSize.y*t), oldSize.z);
        return surface(surface.id(), newSize, surface.origin);
    }

    /**
     * Take the dimensions of a surface and return a list of dimensions of
     * parts that exactly fill the given surface.
     * The dimensions (and areas) of parts are normally distributed.
     * Implemented by randomly slicing a randomly chosen part.
     */
    public List<Part> fillSurfaceNormally(final Surface surface, final int parts) {
        List<Part> done = new ArrayList<>();
        List<Part> slicable = new ArrayList<>();
        Part p1 = new Part();
        p1.setId(0);
        p1.setSize(surface.size());
        p1.setPosition(Dimensions.ZERO.withZ(surface.id()));
        p1.setSideDown(Side.bottom);
        p1.setRotation(Rotation.ZERO);
        slicable.add(p1);
        // Randomly remove one part, slice it, and add the two halves.
        // Loop until desired number of parts have been added.
        while (slicable.size() + done.size() < parts) {
            final var part = rng.removeRandomElement(slicable);
            // XXX: check each side individually? (Keep slicing one dimension if possible?)
            if (part.getSize().x/2 <= minPartSide || part.getSize().y/2 <= minPartSide) {
                done.add(part);
                continue;
            }
            slicable.addAll(randomSlice(part));
        }
        done.addAll(slicable);
        // Randomize heights.
        for (final var part : done) {
            final var size = part.getSize();
            final int height = rng.nextInt(minPartSide, surfaceHeight);
            part.setSize(size.withZ(height));
        }
        if (done.size() != parts) {
            throw new AssertionError("Wtf: " + done.size() + " != " + parts);
        }
        return done;
    }

    /**
     * Take the dimensions of a surface and return a list of dimensions of
     * parts that exactly fill the given surface.
     * The dimensions (and areas) of parts are uniformly distributed.
     */
    public List<Dimensions> fillSurfaceUniformly(final Dimensions surface) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    public List<Part> randomSlice(final Part part) {
        final boolean sliceX = rng.nextBoolean();
        if (part.getSize().x > minPartSide && sliceX) {
            return randomSliceX(part);
        } else {
            return randomSliceY(part);
        }
    }

    /*
     * +------+--------+
     * |x,y   |x2,y2   |
     * |      |        | d
     * |      |        |
     * +------+--------+
     *       w1       w2
     */
    public List<Part> randomSliceX(final Part p1) {
        final Dimensions size = p1.getSize();
        final int max = size.x;
        assert max >= minPartSide;
        // w1 is the new width of part 1.
        final int w1 = rng.nextInt(minPartSide, max-minPartSide);
        assert w1 >= minPartSide;
        // w2, the width of the new part, is the remainder of the old part.
        final int w2 = size.x - w1;
        assert w2 >= minPartSide;
        // The old part retains its position.
        p1.setSize(size.withX(w1));
        // Create the new part.
        final Part p2 = new Part();
        p2.setSize(size.withX(w2));
        // The new part moves to the position of the slice.
        p2.setPosition(p1.getPosition().plus(dimensions(w1, 0, 0)));
        p2.setSideDown(p1.getSideDown());
        p2.setRotation(p1.getRotation());
        p2.setId(p1.getId() + 1);
        return List.of(p1, p2);
    }

    /*
     * +-------------+
     * |x,y          |
     * |             |
     * +-------------+ d1
     * |x2,y2        |
     * +-------------+ d2
     *        w
     */
    /** Modifies p1 by slicing it into two parts - returns both parts. */
    public List<Part> randomSliceY(final Part p1) {
        final Dimensions size = p1.getSize();
        final int max = size.y;
        assert max >= minPartSide;
        // d1 is the new depth of part 1.
        final int d1 = rng.nextInt(minPartSide, max-minPartSide);
        assert d1 >= minPartSide;
        // d2, the depth of the new part, is the remainder of the old part.
        final int d2 = size.y - d1;
        assert d2 >= minPartSide;
        // The old part retains its position.
        p1.setSize(size.withY(d1));
        // Create the new part.
        final Part p2 = new Part();
        p2.setSize(size.withY(d2));
        // The new part moves to the position of the slice.
        p2.setPosition(p1.getPosition().plus(dimensions(0, d1, 0)));
        p2.setSideDown(p1.getSideDown());
        p2.setRotation(p1.getRotation());
        p2.setId(p1.getId() + 1);
        return List.of(p1, p2);
    }

    public void sanityCheck(Layout layout) {
        checkPartCount(layout);
        checkPartSides(layout);
        // Check that all part sides are at most maxPartSide.
        checkOptimal(layout);
        checkFeasible(layout);
        checkAllowedSideConsistency(layout);
        checkAllowedSideCount(layout);
        checkSurfaceCount(layout);
        checkSurfaceSides(layout);
    }

    private void checkPartIds(final Layout layout) {
        final long parts = layout.getParts().stream().count();
        final long ids = layout.getParts().stream().map(Part::getId).distinct().count();
        if (parts != ids) {
            throw new AssertionError(parts + " parts but " + ids + " part IDs");
        }
    }

    private void checkSurfaceSides(Layout layout) {
        final var surfaces = layout.getWagon().surfaces();
        for (final var surface : surfaces) {
            final var size = surface.size();
            final var msg = "Surface with id " + surface.id() + " and dimensions " + size;
            if (size.x < minSurfaceSide) {
                throw new AssertionError(msg + " has width < " + minSurfaceSide);
            }
            if (size.y < minSurfaceSide) {
                throw new AssertionError(msg + " has depth < " + minSurfaceSide);
            }
            if (size.x > maxSurfaceSide) {
                throw new AssertionError(msg + " has width > " + maxSurfaceSide);
            }
            if (size.y > maxSurfaceSide) {
                throw new AssertionError(msg + " has depth > " + maxSurfaceSide);
            }
        }
    }

    private void checkPartCount(Layout layout) {
        final var parts = layout.getParts().size();
        if (parts < minParts) {
            final var msg = String.format(
                "Too few parts: %d < %d", parts, minParts
            );
            throw new AssertionError(msg);
        }
        if (parts > maxParts) {
            final var msg = String.format(
                "Too many parts: %d > %d", parts, maxParts
            );
            throw new AssertionError(msg);
        }
    }

    private void checkSurfaceCount(Layout layout) {
        final var surfaces = layout.getWagon().surfaces().size();
        if (surfaces < minSurfaces) {
            final var msg = String.format(
                "Too few surfaces: %d < %d", surfaces, minSurfaces
            );
            throw new AssertionError(msg);
        }
        if (surfaces > maxSurfaces) {
            final var msg = String.format(
                "Too many surfaces: %d > %d", surfaces, maxSurfaces
            );
            throw new AssertionError(msg);
        }
    }

    private void checkAllowedSideCount(Layout layout) {
        for (final var part : layout.getParts()) {
            final var allowed = part.getAllowedDown();
            if (allowed.size() < minAllowedDown) {
                throwTooFewSidesAllowed(part);
            }
            if (allowed.size() > maxAllowedDown) {
                throwTooManySidesAllowed(part);
            }
        }
    }

    private void checkAllowedSideConsistency(Layout layout) {
        for (final var part : layout.getParts()) {
            final var allowed = part.getAllowedDown();
            final var preferred = part.getPreferredDown();
            if (preferred != null && !allowed.contains(preferred)) {
                throwPreferredSideMismatch(part);
            }
            final var hint = part.getHint();
            if (part.hasHint() && !allowed.contains(hint.side().get())) {
                throwHintSideMismatch(part);
            }
        }
    }

    private void checkOptimal(Layout layout) {
        checkFeasible(layout);
        final var soft = SoftScore.getSoftScore(layout);
        // When preferred might be impossible, we tolerate a soft score < 0.
        if (randomPreferredProbability == 0.0 && soft < 0) {
            throw new AssertionError("Layout not optimal. Soft score: " + soft);
        }
    }

    private void checkFeasible(Layout layout) {
        final var hard = HardScore.getHardScore(layout);
        if (hard < 0) {
            System.out.println("Overlap: " + HardScore.countOverlappingParts(layout));
            System.out.println("Outside: " + HardScore.countPartsOutside(layout));
            System.out.println("Misplaced: " + HardScore.countMisplacedParts(layout));
            System.out.println("Disallowed: " + HardScore.countDisallowedSidesDown(layout));
            throw new AssertionError("Layout not feasible. Hard score: " + hard);
        }
    }

    private void checkPartSides(Layout layout) {
        for (final var part : layout.getParts()) {
            if (part.minLength() < minPartSide) {
                throwPartSideTooSmall(part);
            }
        }
    }

    private void throwTooManySidesAllowed(final Part part) {
        final var msg = String.format(
            "Part with id %d has %d > %d sides allowed.",
            part.getAllowedDown().size(), maxAllowedDown
        );
        throw new AssertionError(msg);
    }

    private void throwTooFewSidesAllowed(final Part part) {
        final var msg = String.format(
            "Part with id %d only has %d < %d sides allowed.",
            part.getAllowedDown().size(), minAllowedDown
        );
        throw new AssertionError(msg);
    }

    private void throwHintSideMismatch(final Part part) {
        final var msg = String.format(
            "Hint side inconsistent for part with id %d. "
            + "Hint = %s but allowed = %s",
            part.getHint().side().get(), part.getAllowedDown()
        );
        throw new AssertionError(msg);
    }

    private void throwPreferredSideMismatch(final Part part) {
        final var msg = String.format(
            "Preferred side inconsistent for part with id %d. "
            + "Preferred = %s but allowed = %s",
            part.getPreferredDown(), part.getAllowedDown()
        );
        throw new AssertionError(msg);
    }

    private void throwPartSideTooSmall(final Part part) {
        final var id = part.getId();
        final var size = part.getSize();
        final var msg = String.format(
            "Part with id %d and dimensions %s has a side < %d", id, size, minPartSide
        );
        throw new AssertionError(msg);
    }

}
