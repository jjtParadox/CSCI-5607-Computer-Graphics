import javafx.scene.paint.Color
import kotlin.math.sqrt

// Math-oriented classes

data class RayColor(var r: Double, var g: Double, var b: Double) {
    fun toColor() = Color(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0), 1.0)
}

// Operator overloads and other utility functions
// Defined outside the above class so I can override them later if I implement object pooling
operator fun RayColor.plus(c: RayColor) = copy(r = r + c.r, g = g + c.g, b = b + c.b)
operator fun RayColor.minus(c: RayColor) = copy(r = r - c.r, g = g - c.g, b = b - c.b)
operator fun RayColor.times(c: RayColor) = copy(r = r * c.r, g = g * c.g, b = b * c.b)
operator fun RayColor.times(d: Double) = copy(r = r * d, g = g * d, b = b * d)
operator fun RayColor.div(d: Double) = copy(r = r / d, g = g / d, b = b / d)
fun RayColor.lerp(c: RayColor, a: Double): RayColor {
    val i = r*(1.0-a) + c.r*a
    val j = g*(1.0-a) + c.g*a
    val k = b*(1.0-a) + c.b*a
    return copy(r = i, g = j, b = k)
}


data class Point3d(var x: Double, var y: Double, var z: Double)

operator fun Point3d.plus(v: Vector3d) = copy(x = x + v.x, y = y + v.y, z = z + v.z)
operator fun Point3d.minus(p: Point3d) = Vector3d(x - p.x, y - p.y, z - p.z)
operator fun Point3d.minus(v: Vector3d) = Point3d(x - v.x, y - v.y, z - v.z)
fun Point3d.lerp(p: Point3d, a: Double): Point3d {
    val i = x*(1.0-a) + p.x*a
    val j = y*(1.0-a) + p.y*a
    val k = z*(1.0-a) + p.z*a
    return copy(x = i, y = j, z = k)
}
fun Point3d.distanceSquared(p: Point3d) = (this - p).lengthSquared


data class Vector3d(var x: Double, var y: Double, var z: Double) {
    val length: Double get() = sqrt(lengthSquared)
    val lengthSquared: Double get() = x*x + y*y + z*z

    fun normalize() {
        scale(1.0/length)
    }

    fun scale(d: Double) {
        x *= d
        y *= d
        z *= d
    }

    fun negate() {
        x = -x
        y = -y
        z = -z
    }
}

operator fun Vector3d.plus(v: Vector3d) = copy(x = x + v.x, y = y + v.y, z = z + v.z)
operator fun Vector3d.plus(p: Point3d) = p + this
operator fun Vector3d.minus(v: Vector3d) = copy(x = x - v.x, y = y - v.y, z = z - v.z)
// Infix functions allow me to do "v1 cross v2" instead of "v1.cross(v2)"
infix fun Vector3d.dot(v: Vector3d) = x*v.x + y*v.y + z*v.z
infix fun Vector3d.cross(v: Vector3d): Vector3d {
    val i = y*v.z - z*v.y
    val j = z*v.x - x*v.z
    val k = x*v.y - y*v.x
    return copy(x = i, y = j, z = k)
}
fun Vector3d.lerp(v: Vector3d, a: Double): Vector3d {
    val i = x*(1.0-a) + v.x*a
    val j = y*(1.0-a) + v.y*a
    val k = z*(1.0-a) + v.z*a
    return copy(x = i, y = j, z = k)
}


operator fun Double.times(v: Vector3d) = v.copy().also { it.scale(this) } // Make a copy of the vector and also scale it by the number ('this')


// Unused Pooled operations

//class MathPooler {
//    val ptPooler = Pooler(pointPool)
//    val vecPooler = Pooler(vectorPool)
//
//    operator fun Point3d.plus(t: Tuple3d): Point3d = ptPooler.borrowObject().also {
//        it.set(this)
//        it.add(t)
//    }
//
//    operator fun Point3d.minus(p: Point3d): Vector3d = vecPooler.borrowObject().also {
//        it.set(this)
//        it.sub(p)
//    }
//
//    fun Point3d.lerp(p: Point3d, alpha: Double): Point3d = ptPooler.borrowObject().also {
//        it.set(this)
//        it.interpolate(p, alpha)
//    }
//
//    operator fun Vector3d.plus(v: Vector3d): Vector3d = vecPooler.borrowObject().also {
//        it.set(this)
//        it.add(v)
//    }
//
//    operator fun Vector3d.plus(p: Point3d): Point3d = ptPooler.borrowObject().also {
//        it.set(this)
//        it.add(p)
//    }
//
//    operator fun Vector3d.minus(v: Vector3d): Vector3d = vecPooler.borrowObject().also {
//        it.set(this)
//        it.sub(v)
//    }
//
//    operator fun Double.times(v: Vector3d): Vector3d = vecPooler.borrowObject().also {
//        it.set(v)
//        it.scale(this)
//    }
//
//    infix fun Vector3d.cross(v: Vector3d): Vector3d = vecPooler.borrowObject().also { it.cross(this, v) }
//
//    fun newVec(x: Double, y: Double, z: Double) = vecPooler.borrowObject().apply {
//        this.x = x
//        this.y = y
//        this.z = z
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    inline fun <R> doMath(action: MathPooler.() -> R): R {
//        try {
//            val result = action()
//            return when(result) {
//                is Point3d -> pointPool.borrow().apply { this.set(result) } as R
//                is Vector3d -> vectorPool.borrow().apply { this.set(result) } as R
//                else -> result
//            }
//        } finally {
//            ptPooler.returnAll()
//            vecPooler.returnAll()
//        }
//    }
//
//    fun returnToPool(vararg objs: Any) {
//        for (obj in objs) {
//            when (obj) {
//                is Point3d -> pointPool.giveBack(obj)
//                is Vector3d -> vectorPool.giveBack(obj)
//            }
//        }
//    }
//}
//
//fun <R> mathPool(action: MathPooler.() -> R): R {
//    return MathPooler().doMath(action)
//}
