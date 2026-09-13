# Deep Dive 02: Java Collections, HashMap Internals, and the equals()/hashCode() Contract

> **Module:** `01-java-foundations`  
> **Target Audience:** Beginner Interns to Senior Backend Engineers / Tier-1 Interview Candidates  
> **Prerequisites:** Basic Java classes, Arrays, and Object references  
> **Related Code in Project:** DTOs (`GenericResponse.java`, `SignUpRequest.java`), Entities (`User.java`, `Role.java`), Cache keys  
> **Last Verified Against:** Java 21 LTS  

---

## 🗺️ Visual Reading Order & Navigation
```text
[01_OOP_SOLID_AND_CLEAN_CODE.md]
              │
              ▼
[02_COLLECTIONS_HASHMAP_INTERNALS_EQUALS_HASHCODE.md]  ◄── YOU ARE HERE
              │
              ▼
[03_EXCEPTION_HANDLING_UNCHECKED_VS_CHECKED_AND_OPTIONAL.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Postal Office Analogy
Imagine a massive postal sorting facility receiving 1,000,000 letters every hour. If all letters are dumped into one enormous pile (an unsorted list), finding a letter addressed to "Alice in Seattle" requires scanning through up to 1,000,000 envelopes one by one ($O(N)$ time complexity).

To make search near-instantaneous, the post office builds **16 labeled sorting bins** (numbered 0 to 15):
1. When a letter arrives, a mathematical rule looks at the recipient's name ("Alice") and calculates a bin number (e.g., Bin #7). This is **Hashing (`hashCode()`)**.
2. The letter is tossed directly into Bin #7.
3. When Alice arrives to claim her letter, the postal worker runs the same rule on "Alice", goes straight to Bin #7 ($O(1)$ lookup), and only searches through the small handful of letters inside that specific bin using her exact national ID card. This exact match verification is **Equality (`equals()`)**.

```text
Key ("Alice") ───► hashCode() ───► Math & Bitwise AND ───► Bucket Index [7]
                                                              │
                                            ┌─────────────────┴─────────────────┐
                                            ▼                                   ▼
                                     [Letter 1: Alice]                   [Letter 2: Bob]
                                            │ (Collision!)                      │
                                            └───────► equals("Alice")? ─────────┘
                                                      YES ──► Return Alice's Value!
```

---

## 🟡 Tier 2: Common Doubts, Gotchas & Anti-Patterns

### 1. "Why must I override BOTH `equals()` and `hashCode()`? Why not just `equals()`?"
**The Disaster Scenario:**
Suppose you create an `OrderItem` class and override `equals()` so that two items with the same `sku` are considered equal. But you **forget** to override `hashCode()`.

```java
public class OrderItem {
    private String sku;
    // Overrode equals() based on sku, but LEFT default Object.hashCode()!
}
```

Now watch what happens in a `HashSet` or `HashMap`:
```java
Set<OrderItem> cart = new HashSet<>();
OrderItem item1 = new OrderItem("SKU-LAPTOP-01");
OrderItem item2 = new OrderItem("SKU-LAPTOP-01");

cart.add(item1);
System.out.println(cart.contains(item2)); // PRINTS FALSE! Why?!
```
**Why it fails:**
- `Object.hashCode()` calculates the hash code based on the **internal memory address** of the object.
- `item1` and `item2` are two distinct objects in heap memory, so they get completely different hash codes (e.g., `item1` -> Bin 3, `item2` -> Bin 11).
- When `cart.contains(item2)` is called, Java computes `item2.hashCode()`, looks **only in Bin 11**, finds it empty, and immediately returns `false`!
- Java **never even called your `equals()` method** because it looked in the wrong bucket!

> [!CAUTION]
> **The Golden Contract Rule:**
> 1. If `a.equals(b) == true`, then `a.hashCode()` **MUST** equal `b.hashCode()`.
> 2. If `a.hashCode() == b.hashCode()`, `a.equals(b)` does **NOT** have to be true (this is simply a *hash collision*).
> 3. If you break Rule 1, `HashMap`, `HashSet`, and `ConcurrentHashMap` will silently corrupt your data or fail lookups.

---

### 2. "Why must HashMap keys be IMMUTABLE?"
Consider using a mutable object as a HashMap key:
```java
Map<Customer, Order> orders = new HashMap<>();
Customer john = new Customer("John", "Tier-1");
orders.put(john, new Order("ORDER-999"));

// Later in code... someone mutates the key!
john.setTier("Tier-2");

// Now try to fetch John's order:
Order order = orders.get(john); // RETURNS NULL!
```
**What just happened?**
1. At `put()` time, `john` had `hashCode() = 45012`, placing it into **Bucket 4**.
2. After `john.setTier("Tier-2")`, his `hashCode()` dynamically changed to `89123`.
3. At `get()` time, `orders.get(john)` computes the new hash code (`89123`), which routes to **Bucket 9**.
4. Bucket 9 is completely empty. John's order is still sitting in Bucket 4, orphaned forever—a classic **memory leak** and silent bug!

> [!TIP]
> Always use immutable types like `String`, `UUID`, `Long`, or Java 16+ `record` as map keys. If you must use a custom class, make all fields `final` and never provide setters.

---

## 🔴 Tier 3: Low-Level Internal Mechanics of `java.util.HashMap`

### 1. The Core Data Structure
In Java 8+, `HashMap` is an array of `Node<K, V>` buckets:
```java
transient Node<K,V>[] table;
```
Each `Node<K,V>` is a singly linked list node containing:
- `final int hash` (precomputed 32-bit hash code)
- `final K key`
- `V value`
- `Node<K,V> next`

```text
table[]
  [0]  ──► null
  [1]  ──► Node(hash, K1, V1) ──► Node(hash, K2, V2) ──► null
  [2]  ──► null
  ...
  [7]  ──► TreeNode (Red-Black Tree Root: O(log N) search)
  ...
  [15] ──► null
```

---

### 2. How the Bucket Index is Calculated (Bitwise Optimization)
In junior implementations, people write: `bucketIndex = hash % table.length`.
However, integer modulo (`%`) requires hardware division, which costs dozens of CPU cycles.

The JDK engineers enforce that `table.length` is **always a power of 2** (e.g., 16, 32, 64, 128...). When $N$ is a power of 2, the mathematical modulo $X \pmod N$ is bitwise equivalent to:
$$\text{bucketIndex} = \text{hash} \ \& \ (N - 1)$$

**Example with default capacity $N = 16$:**
- $N - 1 = 15 = \text{0000 0000 0000 1111}_2$
- If `hash` = `0101 1010 1100 0111`
- Bitwise AND:
```text
    0101 1010 1100 0111  (hash)
&   0000 0000 0000 1111  (16 - 1 = 15)
-----------------------
    0000 0000 0000 0111  = Index 7 (Computed in 1 CPU clock cycle!)
```

---

### 3. The Hash Perturbation Function (Preventing Clustering)
Notice that $(N - 1)$ only checks the **lowest 4 bits** of the hash! If different keys have hashes differing only in the higher 16 bits, they would all collide on the same bucket.

To prevent this, `HashMap` applies an internal **hash spreading / perturbation function**:
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
This XORs the higher 16 bits of the hash with the lower 16 bits (`h >>> 16`), ensuring that variations in higher bits affect the bucket index even for small table sizes.

---

### 4. Collision Resolution: Linked List vs. Red-Black Tree (`TREEIFY_THRESHOLD`)
When multiple keys map to the same bucket index:
1. **Chain Length $\le 7$:** Entries are stored as a singly linked list. Lookup takes $O(K)$ where $K$ is chain length.
2. **Chain Length $\ge 8$ (`TREEIFY_THRESHOLD = 8`) AND Table Capacity $\ge 64$ (`MIN_TREEIFY_CAPACITY = 64`):**
   - The linked list is converted into a balanced **Red-Black Tree** (`TreeNode<K,V>`).
   - Lookup time drops from $O(N)$ worst-case to **$O(\log N)$ guaranteed**.
3. **Chain shrinks to $\le 6$ (`UNTREEIFY_THRESHOLD = 6`):**
   - During resizing, if tree nodes drop to 6 or fewer, the tree is converted back into a linked list to save memory overhead.

---

### 5. Resizing & Rehashing (`loadFactor = 0.75`)
- **Default initial capacity:** 16
- **Default load factor:** 0.75
- **Threshold:** $\text{capacity} \times \text{loadFactor} = 16 \times 0.75 = 12$

When the 13th entry is inserted into the map:
1. The table capacity doubles: $16 \to 32$.
2. All existing entries are rehashed into the new table.
3. Thanks to power-of-two sizing, an entry either stays at index $I$ or moves to $I + \text{oldCapacity}$. No modulo division is needed!

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What happens if two distinct objects return the exact same `hashCode()`? How does `HashMap.put()` handle it?
**High-Scoring Answer:**
> "This is a standard hash collision. `HashMap.put(key, value)`:
> 1. Calculates the bucket index using `hash & (table.length - 1)`.
> 2. Iterates through the linked list (or Red-Black tree) at that bucket.
> 3. For each node, it checks two conditions: `node.hash == hash && (node.key == key || key.equals(node.key))`.
> 4. Since the keys are different (`equals()` returns `false`), it does NOT overwrite the existing value. Instead, it appends a new `Node` at the tail of the linked list (or inserts a new `TreeNode`).
> 5. If the chain length reaches 8 and the table capacity is at least 64, it treeifies the bucket into a Red-Black tree."

---

### Q2: Why is `ConcurrentHashMap` preferred over `Collections.synchronizedMap()` or `Hashtable` in multi-threaded microservices?
**High-Scoring Answer:**
> "`Hashtable` and `Collections.synchronizedMap(map)` synchronize every read and write on a single global mutex lock (`synchronized(this)`). Under high concurrency (e.g. 100 threads in an API gateway or product catalog cache), this creates extreme thread contention and serializes all traffic.
> 
> In contrast, `ConcurrentHashMap` in Java 8+:
> 1. **Lock-free reads:** `get()` operations never lock; they read `volatile` node references, allowing unlimited concurrent readers.
> 2. **Bucket-level striping:** Write operations (`put`, `remove`) lock **only the first node of the specific bucket** being modified using `synchronized(node)` or atomic CAS (`compareAndSet`). Other threads modifying different buckets proceed in parallel without waiting.
> 3. Concurrent read throughput scales almost linearly with CPU cores."

---

### Q3: What is the Time Complexity of `HashMap.get()` in the best, average, and worst cases?
**High-Scoring Answer:**
> - **Best / Average Case:** $O(1)$ constant time. Good hash distribution maps keys directly to unique buckets or chains of length 1-2.
> - **Worst Case (Java 8+):** $O(\log N)$ logarithmic time. If a malicious attacker crafts keys with identical hash codes (Hash Collision Attack), the bucket converts into a balanced Red-Black tree where search is bounded by $O(\log N)$.
> - **Worst Case (Legacy Java 7):** $O(N)$ linear time, because Java 7 used pure singly linked lists with no treeification."

---

### Q4: Can we insert `null` keys or values in `HashMap`, `TreeMap`, and `ConcurrentHashMap`?
| Map Implementation | Allows `null` Key? | Allows `null` Value? | Technical Reason |
| :--- | :---: | :---: | :--- |
| **`HashMap`** | ✅ Yes (at most 1) | ✅ Yes (unlimited) | `hash(null)` explicitly returns 0, always stored in bucket index 0. |
| **`TreeMap`** | ❌ No (NullPointerException) | ✅ Yes | Needs to call `key.compareTo(otherKey)` to sort entries. Calling `null.compareTo()` throws NPE. |
| **`ConcurrentHashMap`** | ❌ No (NullPointerException) | ❌ No (NullPointerException) | Avoids ambiguity in concurrent environments. In `map.get(k) == null`, it is impossible to atomically know whether the key is missing or mapped to a `null` value without locking. |

---

### Q5: How would you design a high-throughput, LRU (Least Recently Used) Cache using Java Collections?
**High-Scoring Answer:**
> "The cleanest standard JDK approach is extending `LinkedHashMap`:
> 1. Initialize `LinkedHashMap` with `accessOrder = true` (which moves accessed elements to the tail of a doubly linked list on `get()` or `put()`).
> 2. Override the `protected boolean removeEldestEntry(Map.Entry eldest)` method:
> ```java
> public class LruCache<K, V> extends LinkedHashMap<K, V> {
>     private final int maxCapacity;
> 
>     public LruCache(int maxCapacity) {
>         super(maxCapacity, 0.75f, true); // true = access-order
>         this.maxCapacity = maxCapacity;
>     }
> 
>     @Override
>     protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
>         return size() > maxCapacity;
>     }
> }
> ```
> For thread-safe production usage, we wrap it with `Collections.synchronizedMap()` or use a dedicated concurrent cache like Caffeine, which implements Window TinyLFU."
