# Performance

Performance is not a dedicated goal of this library, but here's some numbers:

![Performance graph](performance.png)

![Mem use graph](mem-use.png)

```clojure
; memoize is not thread-safe and doesn't have any features
(def f-memoize (memoize identity))
; clojure.core.memoize
(def f-core-memo (ccm/memo identity))
; memento
(def f-memento (m/memo identity {::m/type ::m/caffeine}))
; memento caffeine variable expiry
(def f-memento-var (m/memo identity {::m/type ::m/caffeine ::m/expiry memento.caffeine.config/meta-expiry}))
; memento light caffeine
(def f-light-memento (m/memo identity {::m/type ::m/light-caffeine}))
```
## Memoize

#### All hits
```text
(cc/bench (f-memoize 1))
Evaluation count : 2911575540 in 60 samples of 48526259 calls.
             Execution time mean : 18,520670 ns
    Execution time std-deviation : 0,632964 ns
   Execution time lower quantile : 18,041806 ns ( 2,5%)
   Execution time upper quantile : 20,272312 ns (97,5%)
                   Overhead used : 1,997090 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 2 (3,3333 %)
 Variance from outliers : 20,6200 % Variance is moderately inflated by outliers

```

#### 1M misses (426ns per miss)
```text
(cc/bench (let [f-memoize (memoize identity)]
            (reduce #(f-memoize %2) (range 1000000))))
Evaluation count : 180 in 60 samples of 3 calls.
             Execution time mean : 426,691729 ms
    Execution time std-deviation : 31,649211 ms
   Execution time lower quantile : 407,433346 ms ( 2,5%)
   Execution time upper quantile : 500,285216 ms (97,5%)
                   Overhead used : 1,997090 ns

Found 9 outliers in 60 samples (15,0000 %)
	low-severe	 5 (8,3333 %)
	low-mild	 4 (6,6667 %)
 Variance from outliers : 55,1467 % Variance is severely inflated by outliers
```

## Clojure Core Memoize

#### All hits

```text
(cc/bench (f-core-memo 1))
Evaluation count : 329229720 in 60 samples of 5487162 calls.
             Execution time mean : 180,803852 ns
    Execution time std-deviation : 3,880666 ns
   Execution time lower quantile : 177,830691 ns ( 2,5%)
   Execution time upper quantile : 189,061520 ns (97,5%)
                   Overhead used : 1,997090 ns

Found 6 outliers in 60 samples (10,0000 %)
	low-severe	 3 (5,0000 %)
	low-mild	 3 (5,0000 %)
 Variance from outliers : 9,4347 % Variance is slightly inflated by outliers
```

#### 1M misses (778 ns per miss)

```text
(cc/bench (let [f-core-memo (ccm/memo identity)]
            (reduce #(f-core-memo %2) (range 1000000))))
Evaluation count : 120 in 60 samples of 2 calls.
             Execution time mean : 778,758053 ms
    Execution time std-deviation : 58,068726 ms
   Execution time lower quantile : 717,950541 ms ( 2,5%)
   Execution time upper quantile : 947,641405 ms (97,5%)
                   Overhead used : 1,997090 ns

Found 6 outliers in 60 samples (10,0000 %)
	low-severe	 4 (6,6667 %)
	low-mild	 2 (3,3333 %)
 Variance from outliers : 55,1627 % Variance is severely inflated by outliers
```

#### 1M misses for size 100 LRU cache (1811 ns per miss)

```text
(cc/bench (let [f-core-memo (ccm/lru identity :lru/threshold 100)]
            (reduce #(f-core-memo %2) (range 1000000))))
Evaluation count : 60 in 60 samples of 1 calls.
             Execution time mean : 1,811235 sec
    Execution time std-deviation : 23,960121 ms
   Execution time lower quantile : 1,773504 sec ( 2,5%)
   Execution time upper quantile : 1,866470 sec (97,5%)
                   Overhead used : 1,997090 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 2 (3,3333 %)
 Variance from outliers : 1,6389 % Variance is slightly inflated by outliers

```

## Memento

#### All hits

```text
(cc/bench (f-memento 1))

Evaluation count : 854138220 in 60 samples of 14235637 calls.
             Execution time mean : 70,745055 ns
    Execution time std-deviation : 2,570125 ns
   Execution time lower quantile : 68,659819 ns ( 2,5%)
   Execution time upper quantile : 74,128774 ns (97,5%)
                   Overhead used : 1,970580 ns

Found 2 outliers in 60 samples (3,3333 %)
	low-severe	 1 (1,6667 %)
	low-mild	 1 (1,6667 %)
 Variance from outliers : 22,2591 % Variance is moderately inflated by outliers


```

#### 1M misses (474 ns per miss)

```text
(cc/bench (let [f-memento (m/memo identity {::m/type ::m/caffeine})]
            (reduce #(f-memento %2) (range 1000000))))
Evaluation count : 120 in 60 samples of 2 calls.
             Execution time mean : 474,650866 ms
    Execution time std-deviation : 76,082064 ms
   Execution time lower quantile : 365,465019 ms ( 2,5%)
   Execution time upper quantile : 641,739223 ms (97,5%)
                   Overhead used : 1,992837 ns

```

#### 1M misses for size 100 LRU cache (338 ns per miss)

```text
(cc/bench (let [f-memento (m/memo identity {::m/size< 100 ::m/type ::m/caffeine})]
            (reduce #(f-memento %2) (range 1000000))))
Evaluation count : 180 in 60 samples of 3 calls.
             Execution time mean : 338,339882 ms
    Execution time std-deviation : 15,865012 ms
   Execution time lower quantile : 321,764748 ms ( 2,5%)
   Execution time upper quantile : 370,249429 ms (97,5%)
                   Overhead used : 1,970580 ns

Found 4 outliers in 60 samples (6,6667 %)
	low-severe	 3 (5,0000 %)
	low-mild	 1 (1,6667 %)
 Variance from outliers : 33,5491 % Variance is moderately inflated by outliers


```

## Memento Variable Expiry

#### All hits

```text
(cc/bench (f-memento-var 1))

Evaluation count : 453412980 in 60 samples of 7556883 calls.
             Execution time mean : 132,501700 ns
    Execution time std-deviation : 2,015071 ns
   Execution time lower quantile : 130,326931 ns ( 2,5%)
   Execution time upper quantile : 134,890796 ns (97,5%)
                   Overhead used : 1,978672 ns

```

#### 1M misses (526 ns per miss)

```text
(cc/bench (let [f-memento-var (m/memo identity {::m/type ::m/caffeine ::m/expiry memento.caffeine.config/meta-expiry})]
            (reduce #(f-memento-var %2) (range 1000000))))
Evaluation count : 120 in 60 samples of 2 calls.
             Execution time mean : 526,197766 ms
    Execution time std-deviation : 59,110910 ms
   Execution time lower quantile : 426,811124 ms ( 2,5%)
   Execution time upper quantile : 644,451645 ms (97,5%)
                   Overhead used : 1,978672 ns

```

#### 1M misses for size 100 LRU cache (387 ns per miss)

```text
(cc/bench (let [f-memento-var (m/memo identity {::m/size< 100 ::m/type ::m/caffeine ::m/expiry memento.caffeine.config/meta-expiry})]
            (reduce #(f-memento-var %2) (range 1000000))))
Evaluation count : 180 in 60 samples of 3 calls.
             Execution time mean : 423,554590 ms
    Execution time std-deviation : 7,825220 ms
   Execution time lower quantile : 414,372683 ms ( 2,5%)
   Execution time upper quantile : 435,451863 ms (97,5%)
                   Overhead used : 1,978672 ns

```