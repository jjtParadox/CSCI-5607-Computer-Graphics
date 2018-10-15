import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

abstract class ObjectPool<T> {

    private val objects: Queue<T>
    val size: Int
        get() = objects.size

    var created = 0
        private set

    var borrowed = 0
        private set

    var returned = 0
        private set

    constructor() {
        this.objects = ConcurrentLinkedQueue()
    }

    constructor(objects: Collection<T>) {
        this.objects = ConcurrentLinkedQueue(objects)
    }

    abstract fun create(): T

    open fun clean(obj: T) {}

    fun borrow(): T = objects.poll().also { borrowed++ } ?: create().also { created++ }

    fun giveBack(obj: T) {
        returned++
        clean(obj)
        this.objects.offer(obj)
    }
}

inline fun <T, R> ObjectPool<T>.withObject(action: T.() -> R): R {
    val obj = this.borrow()
    try {
        return action(obj)
    } finally {
        this.giveBack(obj)
    }
}

class Pooler<T>(private val pool: ObjectPool<T>) {
    private val objects = LinkedList<T>()

    fun borrowObject(): T {
        val obj = pool.borrow()
        objects.add(obj)
        return obj
    }

    fun returnAll() {
        while (objects.peek() != null) {
            pool.giveBack(objects.pop())
        }
    }
}
