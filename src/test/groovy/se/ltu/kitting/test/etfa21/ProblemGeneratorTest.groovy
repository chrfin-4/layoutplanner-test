package se.ltu.kitting.test.etfa21;

import spock.lang.*;

import se.ltu.kitting.model.*;
import se.ltu.kitting.test.Rng;
import static se.ltu.kitting.model.Dimensions.dimensions;
import static se.ltu.kitting.model.Dimensions.dimensions as position;

public class ProblemGeneratorTest extends Specification {

    def "test randomSliceY"() {
        given:
            def rng = Stub(Rng)
            rng.nextInt(_, _) >> 40
            def gen = new ProblemGenerator(rng)
            def size = dimensions(100,100,100)
            def pos = position(10,10,0)
            def part1 = new Part()
            part1.setSize(size)
            part1.setPosition(pos)
        when:
            def pair = gen.randomSliceY(part1)
            def p1 = pair[0]
            def p2 = pair[1]
        then:
            p1.getSize() == dimensions(100,40,100)
            p2.getSize() == dimensions(100,60,100)
            p1.getPosition() == pos
            p2.getPosition() == position(10,50,0)
    }

    def "test randomSliceX"() {
        given:
            def rng = Stub(Rng)
            rng.nextInt(_, _) >> 40
            def gen = new ProblemGenerator(rng)
            def size = dimensions(100,100,100)
            def pos = position(10,10,0)
            def part1 = new Part()
            part1.setSize(size)
            part1.setPosition(pos)
        when:
            def pair = gen.randomSliceX(part1)
            def p1 = pair[0]
            def p2 = pair[1]
        then:
            p1.getSize() == dimensions(40,100,100)
            p2.getSize() == dimensions(60,100,100)
            p1.getPosition() == pos
            p2.getPosition() == position(50,10,0)
    }

    def "test shrink surface 1"() {
        given:
            def rng = Stub(Rng)
            rng.nextDouble(_, _) >> 0.5
            def gen = new ProblemGenerator(rng)
            def size = dimensions(1000,1000,100)
            def surface = Surface.surface(0, size, Dimensions.ZERO)
        when:
            def smaller = gen.shrinkToDensity(surface, 0.5).size()
        then:
            smaller.x * smaller.y < size.x * size.y
            smaller.x == 1000
            smaller.y == 1000*0.5
    }

    def "test shrink surface 2"() {
        given:
            def rng = Stub(Rng)
            rng.nextDouble(_, _) >> 1.0
            def gen = new ProblemGenerator(rng)
            def size = dimensions(1000,1000,100)
            def surface = Surface.surface(0, size, Dimensions.ZERO)
        when:
            def smaller = gen.shrinkToDensity(surface, 0.75).size()
        then:
            smaller.x * smaller.y < size.x * size.y
            smaller.x == 1000*0.75
            smaller.y == 1000
    }

    def "test relabel sides"() {
        given:
            def gen = new ProblemGenerator(123) // This test does not rely on RNG.
            def size = dimensions(1,2,3)
            def part = new Part()
            part.setSize(size)
            part.setRotation(Rotation.Z90)
            part.setSideDown(Side.left)
        when:
            gen.relabelSides(part)
        then:
            part.getSize() == dimensions(3,1,2)
            part.currentDimensions() == dimensions(1,2,3)
    }

    def "test random allowed side 1"() {
        given:
            def allSides = Side.values() as List
            def rng = Stub(Rng)
            rng.selectRandomElement(_) >> Side.left
            def gen = new ProblemGenerator(rng)
            def part = new Part()
            part.setAllowedDown(allSides)
        expect:
            part.getAllowedDown() == allSides
            gen.randomAllowedSide(part) == Side.left
    }

    def "test santiy check"() {
        given:
            def gen = new ProblemGenerator(System.currentTimeMillis())
            for (int i = 0; i < 200; i++) {
                gen.randomLayout()
            }
            def layout = gen.randomLayout()
        expect:
            layout != null
    }

}
