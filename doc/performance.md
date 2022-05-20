# Performance

## outdated
Performance is not a dedicated goal of this library, but here's some numbers:

```clojure
; memoize is not thread-safe and doesn't have any features
(def f-memoize (memoize identity))
; clojure.core.memoize
(def f-core-memo (ccm/memo identity))
; memento
(def f-memento (m/memo identity {::m/type ::m/caffeine}))
```
## Memoize

#### All hits
```text
(cc/bench (f-memoize 1))
Evaluation count : 573973500 in 60 samples of 9566225 calls.
             Execution time mean : 100,615709 ns
    Execution time std-deviation : 7,491582 ns
   Execution time lower quantile : 92,569005 ns ( 2,5%)
   Execution time upper quantile : 117,957469 ns (97,5%)
                   Overhead used : 7,616991 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 2 (3,3333 %)
 Variance from outliers : 55,1583 % Variance is severely inflated by outliers
```

#### 1M misses (740ns per miss)
```text
(cc/bench (let [f-memoize (memoize identity)]
            (reduce #(f-memoize %2) (range 1000000))))
Evaluation count : 120 in 60 samples of 2 calls.
             Execution time mean : 746,383750 ms
    Execution time std-deviation : 40,427585 ms
   Execution time lower quantile : 699,218001 ms ( 2,5%)
   Execution time upper quantile : 849,163469 ms (97,5%)
                   Overhead used : 6,449047 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 2 (3,3333 %)
 Variance from outliers : 40,1256 % Variance is moderately inflated by outliers
```

## Clojure Core Memoize

#### All hits

```text
(cc/bench (f-core-memo 1))
Evaluation count : 103323900 in 60 samples of 1722065 calls.
             Execution time mean : 586,999356 ns
    Execution time std-deviation : 27,063768 ns
   Execution time lower quantile : 566,131127 ns ( 2,5%)
   Execution time upper quantile : 643,946885 ns (97,5%)
                   Overhead used : 6,449047 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 2 (3,3333 %)
 Variance from outliers : 31,9721 % Variance is moderately inflated by outliers
```

#### 1M misses (1570 ns per miss)

```text
(cc/bench (let [f-core-memo (ccm/memo identity)]
            (reduce #(f-core-memo %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 1,577084 sec
    Execution time std-deviation : 101,892266 ms
   Execution time lower quantile : 1,494426 sec ( 2,5%)
   Execution time upper quantile : 1,850834 sec (97,5%)
                   Overhead used : 6,449047 ns

Found 3 outliers in 60 samples (5,0000 %)
	low-severe	 3 (5,0000 %)
 Variance from outliers : 48,4403 % Variance is moderately inflated by outliers
```

#### 1M misses for size 100 LRU cache (3800 ns per miss)

```text
(cc/bench (let [f-core-memo (ccm/lru identity :lru/threshold 100)]
            (reduce #(f-core-memo %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 3,808455 sec
    Execution time std-deviation : 182,401054 ms
   Execution time lower quantile : 3,570840 sec ( 2,5%)
   Execution time upper quantile : 4,186359 sec (97,5%)
                   Overhead used : 6,449047 ns
```

## Memento

#### All hits

```text
(cc/bench (f-memento 1))

Evaluation count : 151836660 in 60 samples of 2530611 calls.
             Execution time mean : 372,489623 ns
    Execution time std-deviation : 16,214492 ns
   Execution time lower quantile : 348,557998 ns ( 2,5%)
   Execution time upper quantile : 405,567622 ns (97,5%)
                   Overhead used : 6,642759 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 1 (1,6667 %)
	low-mild	 1 (1,6667 %)
 Variance from outliers : 30,2885 % Variance is moderately inflated by outliers
```

#### 1M misses (1012 ns per miss)

```text
(cc/bench (let [f-memento (m/memo identity {::m/type ::m/caffeine})]
            (reduce #(f-memento %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 1,012604 sec
    Execution time std-deviation : 110,303492 ms
   Execution time lower quantile : 818,881769 ms ( 2,5%)
   Execution time upper quantile : 1,191126 sec (97,5%)
                   Overhead used : 6,642759 ns
=> nil
```

#### 1M misses for size 100 LRU cache (321 ns per miss)

```text
(cc/bench (let [f-memento (m/memo {::m/size< 100 ::m/type ::m/caffeine} identity)]
            (reduce #(f-memento %2) (range 1000000))))
Evaluation count : 240 in 60 samples of 4 calls.
             Execution time mean : 321,756947 ms
    Execution time std-deviation : 21,255531 ms
   Execution time lower quantile : 273,183616 ms ( 2,5%)
   Execution time upper quantile : 369,558286 ms (97,5%)
                   Overhead used : 6,642759 ns

Found 3 outliers in 60 samples (5,0000 %)
	low-severe	 1 (1,6667 %)
	low-mild	 2 (3,3333 %)
 Variance from outliers : 50,0737 % Variance is severely inflated by outliers
```
