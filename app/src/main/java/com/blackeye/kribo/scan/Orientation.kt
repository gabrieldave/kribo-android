package com.blackeye.kribo.scan

/** Logical upright -> encoded file coordinates. Angles are clockwise viewing rotations. */
class Orientation(val rawW: Int, val rawH: Int, val tag: Int) {
    init { require(rawW > 0 && rawH > 0 && tag in 1..8) }
    val width = if (tag >= 5) rawH else rawW
    val height = if (tag >= 5) rawW else rawH
    fun rawX(x: Int, y: Int): Int = when (tag) {
        1,4 -> x
        2,3 -> rawW - 1 - x
        5,6 -> y
        else -> rawW - 1 - y
    }
    fun rawY(x: Int, y: Int): Int = when (tag) {
        1,2 -> y
        3,4 -> rawH - 1 - y
        5,8 -> x
        else -> rawH - 1 - x
    }
}
