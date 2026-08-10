package com.portroyale.tiny.diagnostics

import kotlin.random.Random

/**
 * The single source of chance in the game world - every combat roll, every captain rolled at the
 * tavern, every pirate's tier.
 *
 * It exists to be *seeded*. Scattered `kotlin.random.Random` calls cannot be reproduced or even
 * observed, which is why the tests around combat had to be written with statistical margins
 * rather than exact expectations, and why "a pirate got stuck" had to be diagnosed by reading
 * code rather than by re-running it. With one seed per session, written into the log and shown
 * in the debug overlay, a report can say which one it was.
 *
 * Honest about what this does and does not buy: the seed makes the *random* half reproducible,
 * not the whole session. Replaying still needs the same inputs in the same order, and this is not
 * a lockstep replay system. What it does give is a fight or a spawn that can be re-run instead of
 * guessed at, and tests that can pin exact outcomes ([withSeed]).
 */
object GameRandom {

    /** The seed this session is running on - logged at startup and shown in the debug overlay. */
    var seed: Long = 0L
        private set

    private var random: Random = Random(0L)

    init {
        reseed(Random.Default.nextLong())
    }

    /** Starts a fresh sequence. Called once at startup with a rolled seed, or by hand with a
     *  known one to reproduce something. */
    fun reseed(newSeed: Long) {
        seed = newSeed
        random = Random(newSeed)
    }

    fun nextFloat(): Float = random.nextFloat()

    /** [from] inclusive, [until] exclusive - same contract as kotlin.random.Random.nextInt. */
    fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)

    fun nextInt(until: Int): Int = random.nextInt(until)

    /** Both ends inclusive, matching libGDX's MathUtils.random(int, int), which this replaces. */
    fun nextIntInclusive(from: Int, to: Int): Int = random.nextInt(from, to + 1)

    /** Both ends inclusive, matching MathUtils.random(float, float). */
    fun nextFloat(from: Float, to: Float): Float = from + random.nextFloat() * (to - from)

    fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

    /**
     * Picks one item with a probability proportional to [weight]. Used to give AI ships home
     * waters: a destination near a ship's anchor is likelier than a distant one, without the hard
     * edge a radius cutoff would produce.
     *
     * Non-positive weights are treated as zero. If every weight is zero the choice falls back to
     * a plain uniform pick, so a caller can never be left without a destination.
     */
    fun <T> pickWeighted(items: List<T>, weight: (T) -> Float): T {
        val weights = items.map { weight(it).coerceAtLeast(0f) }
        val total = weights.sum()
        if (total <= 0f) return pick(items)
        var roll = random.nextFloat() * total
        for (index in items.indices) {
            roll -= weights[index]
            if (roll <= 0f) return items[index]
        }
        return items.last() // only reachable through float rounding
    }

    /**
     * Runs [block] on a known seed and restores the previous sequence afterwards. This is what
     * lets a test assert an exact outcome of something that is random in play - and what lets a
     * reported seed be replayed without disturbing anything else.
     */
    fun <T> withSeed(seed: Long, block: () -> T): T {
        val previousSeed = this.seed
        val previousRandom = random
        reseed(seed)
        try {
            return block()
        } finally {
            this.seed = previousSeed
            random = previousRandom
        }
    }
}
