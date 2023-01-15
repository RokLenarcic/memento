# Performance

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
Evaluation count : 1546493460 in 60 samples of 25774891 calls.
             Execution time mean : 31,903999 ns
    Execution time std-deviation : 4,406628 ns
   Execution time lower quantile : 25,492342 ns ( 2,5%)
   Execution time upper quantile : 43,308680 ns (97,5%)
                   Overhead used : 7,742808 ns

Found 4 outliers in 60 samples (6,6667 %)
	low-severe	 3 (5,0000 %)
	low-mild	 1 (1,6667 %)
 Variance from outliers : 82,3854 % Variance is severely inflated by outliers
```

#### 1M misses (886ns per miss)
```text
(cc/bench (let [f-memoize (memoize identity)]
            (reduce #(f-memoize %2) (range 1000000))))
Evaluation count : 120 in 60 samples of 2 calls.
             Execution time mean : 886,890064 ms
    Execution time std-deviation : 122,979964 ms
   Execution time lower quantile : 743,431812 ms ( 2,5%)
   Execution time upper quantile : 1,157114 sec (97,5%)
                   Overhead used : 7,742808 ns

Found 14 outliers in 60 samples (23,3333 %)
	low-severe	 10 (16,6667 %)
	low-mild	 4 (6,6667 %)
 Variance from outliers : 82,3928 % Variance is severely inflated by outliers
```

## Clojure Core Memoize

#### All hits

```text
(cc/bench (f-core-memo 1))
Evaluation count : 160426440 in 60 samples of 2673774 calls.
             Execution time mean : 391,539866 ns
    Execution time std-deviation : 40,805008 ns
   Execution time lower quantile : 364,195529 ns ( 2,5%)
   Execution time upper quantile : 499,366982 ns (97,5%)
                   Overhead used : 7,742808 ns

Found 7 outliers in 60 samples (11,6667 %)
	low-severe	 7 (11,6667 %)
 Variance from outliers : 72,0439 % Variance is severely inflated by outliers
```

#### 1M misses (1610 ns per miss)

```text
(cc/bench (let [f-core-memo (ccm/memo identity)]
            (reduce #(f-core-memo %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 1,612648 sec
    Execution time std-deviation : 278,357346 ms
   Execution time lower quantile : 1,367147 sec ( 2,5%)
   Execution time upper quantile : 2,382284 sec (97,5%)
                   Overhead used : 7,742808 ns

Found 5 outliers in 60 samples (8,3333 %)
	low-severe	 3 (5,0000 %)
	low-mild	 2 (3,3333 %)
 Variance from outliers : 87,6513 % Variance is severely inflated by outliers
```

#### 1M misses for size 100 LRU cache (4470 ns per miss)

```text
(cc/bench (let [f-core-memo (ccm/lru identity :lru/threshold 100)]
            (reduce #(f-core-memo %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 4,472011 sec
    Execution time std-deviation : 170,058847 ms
   Execution time lower quantile : 4,305469 sec ( 2,5%)
   Execution time upper quantile : 4,898280 sec (97,5%)
                   Overhead used : 7,742808 ns

Found 5 outliers in 60 samples (8,3333 %)
	low-severe	 5 (8,3333 %)
 Variance from outliers : 23,8745 % Variance is moderately inflated by outliers

```

## Memento

#### All hits

```text
(cc/bench (f-memento 1))

Evaluation count : 168834720 in 60 samples of 2813912 calls.
             Execution time mean : 379,041867 ns
    Execution time std-deviation : 17,696509 ns
   Execution time lower quantile : 352,242486 ns ( 2,5%)
   Execution time upper quantile : 413,248058 ns (97,5%)
                   Overhead used : 7,742808 ns
```

#### 1M misses (791 ns per miss)

```text
(cc/bench (let [f-memento (m/memo identity {::m/type ::m/caffeine})]
            (reduce #(f-memento %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 791,564839 ms
    Execution time std-deviation : 202,175087 ms
   Execution time lower quantile : 525,390526 ms ( 2,5%)
   Execution time upper quantile : 1,324654 sec (97,5%)
                   Overhead used : 7,742808 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 2 (3,3333 %)
 Variance from outliers : 94,6397 % Variance is severely inflated by outliers
=> nil
```

#### 1M misses for size 100 LRU cache (202 ns per miss)

```text
(cc/bench (let [f-memento (m/memo {::m/size< 100 ::m/type ::m/caffeine} identity)]
            (reduce #(f-memento %2) (range 1000000))))
Evaluation count : 240 in 60 samples of 4 calls.
             Execution time mean : 202,618212 ms
    Execution time std-deviation : 36,929352 ms
   Execution time lower quantile : 120,895867 ms ( 2,5%)
   Execution time upper quantile : 266,305832 ms (97,5%)
                   Overhead used : 7,742808 ns
```
