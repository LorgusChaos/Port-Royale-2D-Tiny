package com.portroyale.tiny.ui

/** Picks the display mode for a requested fullscreen size: the first of [available] whose
 *  [size] equals ([width], [height]) exactly, else the [desktop]'s current mode, so a monitor
 *  without that resolution still gets a working fullscreen (REVIEW.md, finding 23). */
fun <M> chooseDisplayMode(
    available: List<M>,
    width: Int,
    height: Int,
    desktop: M,
    size: (M) -> Pair<Int, Int>
): M = available.firstOrNull { size(it) == (width to height) } ?: desktop
