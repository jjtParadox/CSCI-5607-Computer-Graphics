import javax.vecmath.Point3d
import javax.vecmath.Tuple3d
import javax.vecmath.Vector3d

// Non-pooled operations

operator fun Point3d.plus(t: Tuple3d) = Point3d(this).apply { this.add(t) }
operator fun Point3d.minus(p: Point3d) = Vector3d(this).apply { this.sub(p) }
operator fun Point3d.minus(v: Vector3d) = Point3d(this).apply { this.sub(v) }
fun Point3d.lerp(p: Point3d, alpha: Double) = Point3d(this).apply { this.interpolate(p, alpha) }

operator fun Vector3d.plus(v: Vector3d) = Vector3d(this).apply { this.add(v) }
operator fun Vector3d.plus(p: Point3d) = Point3d(this).apply { this.add(p) }

operator fun Vector3d.minus(v: Vector3d) = Vector3d(this).apply { this.sub(v) }

operator fun Double.times(v: Vector3d) = Vector3d(v).also { it.scale(this) }

infix fun Vector3d.dot(v: Vector3d) = this.dot(v)
infix fun Vector3d.cross(v: Vector3d) = Vector3d(this).also{ it.cross(this, v) }


// Pooled operations

//class Point3dFactory : BasePooledObjectFactory<Point3d>() {
//    override fun create() = Point3d(0.0, 0.0, 0.0)
//    override fun wrap(pt: Point3d?) = DefaultPooledObject<Point3d>(pt)
//    override fun passivateObject(p: PooledObject<Point3d>) {
//        p.`object`.set(0.0, 0.0, 0.0)
//    }
//}
//
//val pointPool = GenericObjectPool<Point3d>(Point3dFactory()).apply {
//    this.maxTotal = -1
//    this.maxIdle = -1
//    for (x in 1..10) this.addObject()
//}
//
//class Vector3dFactory : BasePooledObjectFactory<Vector3d>() {
//    override fun create() = Vector3d(0.0, 0.0, 0.0)
//    override fun wrap(vec: Vector3d?) = DefaultPooledObject<Vector3d>(vec)
//    override fun passivateObject(p: PooledObject<Vector3d>) {
//        p.`object`.set(0.0, 0.0, 0.0)
//    }
//}
//
//val vectorPool = GenericObjectPool<Vector3d>(Vector3dFactory()).apply {
//    this.maxTotal = -1
//    this.maxIdle = -1
//    for (x in 1..10) this.addObject()
//}

val pointPool = object: ObjectPool<Point3d>() {
    override fun create() = Point3d(0.0, 0.0, 0.0)
    override fun clean(obj: Point3d) {
        obj.set(0.0, 0.0, 0.0)
    }
}

val vectorPool = object: ObjectPool<Vector3d>() {
    override fun create() = Vector3d(0.0, 0.0, 0.0)
    override fun clean(obj: Vector3d) {
        obj.set(0.0, 0.0, 0.0)
    }
}

class MathPooler {
    val ptPooler = Pooler(pointPool)
    val vecPooler = Pooler(vectorPool)

    operator fun Point3d.plus(t: Tuple3d): Point3d = ptPooler.borrowObject().also {
        it.set(this)
        it.add(t)
    }

    operator fun Point3d.minus(p: Point3d): Vector3d = vecPooler.borrowObject().also {
        it.set(this)
        it.sub(p)
    }

    fun Point3d.lerp(p: Point3d, alpha: Double): Point3d = ptPooler.borrowObject().also {
        it.set(this)
        it.interpolate(p, alpha)
    }

    operator fun Vector3d.plus(v: Vector3d): Vector3d = vecPooler.borrowObject().also {
        it.set(this)
        it.add(v)
    }

    operator fun Vector3d.plus(p: Point3d): Point3d = ptPooler.borrowObject().also {
        it.set(this)
        it.add(p)
    }

    operator fun Vector3d.minus(v: Vector3d): Vector3d = vecPooler.borrowObject().also {
        it.set(this)
        it.sub(v)
    }

    operator fun Double.times(v: Vector3d): Vector3d = vecPooler.borrowObject().also {
        it.set(v)
        it.scale(this)
    }

    infix fun Vector3d.cross(v: Vector3d): Vector3d = vecPooler.borrowObject().also { it.cross(this, v) }

    fun newVec(x: Double, y: Double, z: Double) = vecPooler.borrowObject().apply {
        this.x = x
        this.y = y
        this.z = z
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> doMath(action: MathPooler.() -> R): R {
        try {
            val result = action()
            return when(result) {
                is Point3d -> pointPool.borrow().apply { this.set(result) } as R
                is Vector3d -> vectorPool.borrow().apply { this.set(result) } as R
                else -> result
            }
        } finally {
            ptPooler.returnAll()
            vecPooler.returnAll()
        }
    }

    fun returnToPool(vararg objs: Any) {
        for (obj in objs) {
            when (obj) {
                is Point3d -> pointPool.giveBack(obj)
                is Vector3d -> vectorPool.giveBack(obj)
            }
        }
    }
}

fun <R> mathPool(action: MathPooler.() -> R): R {
    return MathPooler().doMath(action)
}
