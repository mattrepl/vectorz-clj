(ns mikera.vectorz.test-matrix-api
  (:refer-clojure :exclude [vector? * - +])
  (:use [clojure test])
  (:use clojure.core.matrix)
  (:require [clojure.core.matrix.operators :refer [+ - *]])
  (:require clojure.core.matrix.compliance-tester)
  (:require [clojure.core.matrix.protocols :as mp])
  (:require [clojure.core.matrix.linear :as li])
  (:require [mikera.vectorz.core :as v])
  (:require [mikera.vectorz.matrix :as m])
  (:require [mikera.vectorz.matrix-api])
  (:require clojure.core.matrix.impl.persistent-vector)
  (:require [clojure.core.matrix.impl.wrappers :as wrap])
  (:import [mikera.matrixx AMatrix Matrixx Matrix])
  (:import [mikera.vectorz Scalar])
  (:import [mikera.indexz AIndex Index])
  (:import [mikera.vectorz AVector Vectorz Vector])
  (:import [mikera.arrayz INDArray Array NDArray]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; note - all the operators are core.matrix operators

(set-current-implementation :vectorz)

(deftest test-misc-regressions
  (let [v1 (v/vec1 1.0)]
    (is (array? v1))
    (is (== 1 (dimensionality v1)))
    (is (== 1 (ecount v1)))
    (is (not (matrix? v1))))
  (let [m (coerce (matrix [[1 2]]) [[1 2] [3 4]])] 
    (is (every? true? (map == (range 1 (inc (long (ecount m)))) (eseq m)))))
  (let [m (matrix [[1 2] [3 4]])] 
    (is (== 2 (ecount (first (slices m)))))
    (scale! (first (slices m)) 2.0)
    (is (equals m [[2 4] [3 4]])))
  (let [m (matrix [[0 0] [0 0]])] 
    (assign! m [[1 2] [3 4]])
    (is (equals m [[1 2] [3 4]]))
    (assign! m [[0 0] [0 0]])
    (is (equals m [[0 0] [0 0]]))
    (mp/assign-array! m (double-array [2 4 6 8]))
    (is (equals m [[2 4] [6 8]]))
    (mp/assign-array! m (double-array 4))
    (is (equals m [[0 0] [0 0]])))
  (let [v (v/vec [1 2 3])]
    (is (equals [2 4 6] (add v v))))
  (let [v (Vector/of (double-array 0))]
    (is (== 10 (reduce (fn [acc _] (inc (long acc))) 10 (eseq v))))
    (is (== 10 (ereduce (fn [acc _] (inc (long acc))) 10 v))))
  (let [m (reshape (array (double-array (range 9))) [3 3])]
    (is (equals [[0 1 2]] (submatrix m 0 [0 1])))
    (is (equals [[0 1 2]] (submatrix m [[0 1] nil])))
    (is (equals [[0] [3] [6]] (submatrix m 1 [0 1]))))
  (let [v (array (range 9))]
    (is (equals v (submatrix v [nil])))
    (is (equals v (submatrix v [[0 9]])))
    (is (equals [2 3 4] (submatrix v 0 [2 3])))
    (is (equals [2 3 4] (submatrix v [[2 3]]))))
  (is (instance? AVector (array [1 2])))
  (is (equals [1 1 1] (div (array [2 2 2]) 2)))
  (is (equals [[1 2] [3 4] [5 6]] (join (array [[1 2] [3 4]]) (array [[5 6]]))))
  (is (equals [[1 3] [2 4] [5 6]] (join (transpose (array [[1 2] [3 4]])) (array [[5 6]]))))
  (is (= 1.0 (slice (array [0 1 2]) 1)))
  (is (mp/set-nd (matrix :vectorz [[1 2][3 4]]) [0 1] 3))
  (testing "Regression with matrix applyOp arity 2"
    (let [t (array :vectorz [[10] [20]])]
      (is (equals [[1] [2]] (emap / t 10))))))

(deftest test-set-indices-62 ;; fix for #62 issue
  (is (equals [[1 1] [2 9]] (set-indices (matrix :vectorz [[1 1] [2 2]]) [[1 1]] [9]))))

(deftest test-infinity-norm ;; fix for #67 issue
  (is (equals 2 (mp/norm (array :vectorz [-2 1]) Double/POSITIVE_INFINITY)))
  (is (equals 3 (mp/norm (array :vectorz [-2 3]) Double/POSITIVE_INFINITY))))

(deftest test-set-column ;; fix for #63 issue
  (let [m (matrix :vectorz [[1 2 3] [3 4 5]])]
    (is (equals [[1 2 10] [3 4 11]] (set-column m 2 [10 11])))))

(deftest test-row-column-matrix
  (let [m (matrix :vectorz [1 2 3])
        rm (row-matrix m)
        cm (column-matrix m)]
    (is (not (equals rm cm)))
    (is (equals rm (transpose cm)))
    (is (equals rm [[1 2 3]]))))

(deftest test-mget-regressions
  (is (== 3 (mget (mset (zero-array [4 4]) 0 2 3) 0 2)))
  (is (== 3 (mget (mset (zero-array [4]) 2 3) 2)))
  (is (== 3 (mget (mset (zero-array []) 3)))))

(deftest test-scalar-arrays
  (is (equals 0 (new-scalar-array :vectorz)))
  (is (equals 3 (scalar-array 3)))
  (is (equals 2 (add 1 (array 1))))
  (is (equals [2 3] (add 1 (array [1 2]))))
  (is (equals [2 3] (add (scalar-array 1) (array [1 2])))))

(deftest test-symmetric?
  (is (symmetric? (array [[1 2] [2 3]])))
  (is (not (symmetric? (array [[1 2] [3 4]]))))
  (is (symmetric? (array [1 2 3])))
  (is (symmetric? (array [[[1 2] [0 0]] [[2 0] [0 1]]])))
  (is (symmetric? (array [[[1 2] [3 0]] [[2 0] [0 1]]])))
  (is (not (symmetric? (array [[[1 2] [0 0]] [[3 0] [0 1]]]))))) 

(deftest test-broadcasting-cases
  (is (equals [[2 3] [4 5]] (add (array [[1 2] [3 4]]) (array [1 1]))))
  (is (equals [[2 3] [4 5]] (add (array [1 1]) (array [[1 2] [3 4]]))))
  (is (equals [[2 4] [6 8]] (mul (array [[1 2] [3 4]]) (scalar-array 2))))
  (is (equals [[2 6] [6 12]] (mul (array [[1 2] [3 4]]) [2 3])))
  (is (equals [[1 4] [3 16]] (pow (array [[1 2] [3 4]]) [1 2]))))

(deftest test-broadcasts
  (is (equals [[2 2] [2 2]] (broadcast 2 [2 2])))
  (is (not (equals [[2 2] [2 2]] (broadcast 2 [2])))))

(deftest test-scalar-add
  (is (equals [2 3 4] (add 1 (array [1 2 3]))))
  (is (equals [2 3 4] (add (array [1 2 3]) 1 0)))) 

(deftest test-ecount
  (is (== 1 (ecount (Scalar. 10))))
  (is (== 2 (ecount (v/of 1 2))))
  (is (== 0 (ecount (Vector/of (double-array 0)))))
  (is (== 0 (count (eseq (Vector/of (double-array 0))))))
  (is (== 0 (ecount (coerce :vectorz []))))
  (is (== 4 (ecount (coerce :vectorz [[1 2] [3 4]]))))
  (is (== 8 (ecount (coerce :vectorz [[[1 2] [3 4]] [[1 2] [3 4]]]))))) 

(deftest test-mutability
  (let [v (v/of 1 2)]
    (is (mutable? v))
    (is (mutable? (first (slice-views v)))))
  (let [v (new-array [3 4 5 6])]
    (is (v/vectorz? v))
    (is (mutable? v))
    (is (mutable? (first (slice-views v))))))

(deftest test-new-array
  (is (instance? AVector (new-array [10])))
  (is (instance? AMatrix (new-array [10 10])))
  (is (instance? INDArray (new-array [3 4 5 6])))) 

(deftest test-sub
  (let [a (v/vec [1 2 3 0 0])
        b (v/vec [1 1 4 0 0])]
    (is (equals [0 1 -1 0 0] (sub a b))))) 

(deftest test-add-product
  (let [a (v/vec [1 2])
        b (v/vec [1 1])]
    (is (equals [2 5] (add-product b a a)))
    (is (equals [3 9] (add-scaled-product b a [1 2] 2)))
    (is (equals [11 21] (add-product b a 10)))
    (is (equals [11 21] (add-product b 10 a))))) 

(deftest test-add-product!
  (let [a (v/vec [1 2])
        b (v/vec [1 1])]
    (add-product! b a a)
    (is (equals [2 5] b))
    (add-scaled! b a -1)
    (is (equals [1 3] b))
    (add-scaled-product! b [0 1] [3 4] 2)
    (is (equals [1 11] b)))) 

(deftest test-coerce
  (is (equals (array [1 2]) (coerce :vectorz [1 2])))
  (is (equals (array [[1 2] [3 4]]) (coerce :vectorz [[1 2] [3 4]])))
  (let [a (v/vec [1 2 3 0 0])
        b (v/vec [1 1 4 0 0])
        r (sub a b)]
    (is (equals [0 1 -1 0 0] (coerce [] r)))
    (is (instance? clojure.lang.IPersistentVector (coerce [] r)))
    ;; (is (instance? INDArray (coerce :vectorz 10.0))) ;; TODO: what should this be??
    )) 

(deftest test-ndarray
  (is (equals [[[1]]] (matrix :vectorz [[[1]]])))
  (is (equals [[[[1]]]] (matrix :vectorz [[[[1]]]])))
  (is (equals [[[1]]] (slice (matrix :vectorz [[[[1]]]]) 0)))
  (is (== 4 (dimensionality (matrix :vectorz [[[[1]]]]))))
  (is (equals [[[1]]] (wrap/wrap-slice (matrix :vectorz [[[[1]]]]) 0)))
  (is (equals [[[[1]]]] (wrap/wrap-nd (matrix :vectorz [[[[1]]]]))))) 

(deftest test-element-equality
  (is (e= (matrix :vectorz [[0.5 0] [0 2]])
          [[0.5 0.0] [0.0 2.0]]))
 ;; TODO: enable this test once fixed version of core.matrix is released
 ;; (is (not (e= (matrix :vectorz [[1 2] [3 4]])
 ;;              [[5 6] [7 8]])))
  )

(deftest test-inverse
  (let [m (matrix :vectorz [[0.5 0] [0 2]])] 
    (is (equals [[2 0] [0 0.5]] (inverse m)))))

(deftest test-det
  (is (== -1.0 (det (matrix :vectorz [[0 1] [1 0]])))))

(defn test-round-trip [m]
  (is (equals m (read-string (str m))))
  ;; TODO edn round-tripping?
  )

(deftest test-round-trips
  (test-round-trip (v/of 1 2))
  (test-round-trip (v/of 1 2 3 4 5))
  (test-round-trip (matrix :vectorz [[1 2 3] [4 5 6]]))
  (test-round-trip (matrix :vectorz [[1 2] [3 4]]))
  (test-round-trip (first (slices (v/of 1 2 3))))
)

(deftest test-equals
  (is (equals (v/of 1 2) [1 2])))

(deftest test-vector-ops
  (testing "addition"
    (is (= (v/of 1 2) (+ (v/of 1 1) [0 1])))
    (is (= (v/of 3 4) (+ (v/of 1 1) (v/of 2 3))))
    (is (= [1.0 2.0] (+ [0 2] (v/of 1 0)))))
  
  (testing "scaling"
    (is (= (v/of 2 4) (* (v/of 1 2) 2)))
    (is (= (v/of 2 4) (scale (v/of 1 2) 2)))
    (is (= (v/of 2 4) (scale (v/of 1 2) 2N)))
    (is (= (v/of 2 4) (scale (v/of 1 2) 2.0))))
  
  (testing "subtraction"
    (is (= (v/of 2 4) (- (v/of 3 5) [1 1])))
    (is (= (v/of 1 2) (- (v/of 2 3) (v/of 1 0) (v/of 0 1))))))

(deftest test-matrix-ops
  (testing "addition"
    (is (= (m/matrix [[2 2] [2 2]]) (+ (m/matrix [[1 1] [2 0]]) 
                                       (m/matrix [[1 1] [0 2]]))))
    (is (= (m/matrix [[2 2] [2 2]]) (+ (m/matrix [[1 1] [2 0]]) 
                                       [[1 1] [0 2]])))
    (is (= [[2.0 2.0] [2.0 2.0]] (+ [[1 1] [0 2]]
                                    (m/matrix [[1 1] [2 0]])))))
  (testing "scaling"
    (is (= (m/matrix [[2 2] [2 2]]) (scale (m/matrix [[1 1] [1 1]]) 2))))
  
  (testing "multiplication"
    (is (= (m/matrix [[8]]) (mmul (m/matrix [[2 2]]) (m/matrix [[2] [2]]))))
    (is (= (m/matrix [[8]]) (mmul (m/matrix [[2 2]]) [[2] [2]])))
    ;; (is (= [[8.0]] (* [[2 2]] (m/matrix [[2] [2]]))))
    ))

(deftest test-join
  (is (= (array [[[1]] [[2]]]) (join (array [[[1]]]) (array [[[2]]])))))

(deftest test-pm
  (is (string? (clojure.core.matrix.impl.pprint/pm (array :vectorz [1 2]))))) 

(deftest test-matrix-transform
  (testing "vector multiple"
    (is (= (v/of 2 4) (mmul (m/matrix [[2 0] [0 2]]) (v/of 1 2))))
    (is (= (v/of 2 4) (mmul (m/scalar-matrix 2 2.0) (v/of 1 2))))
    (is (= (v/of 2 4) (mmul (m/scalar-matrix 2 2.0) [1 2]))))
  (testing "persistent vector transform"
    (is (= (v/of 1 2) (transform (m/identity-matrix 2) [1 2]))))
  (testing "transform in place"
    (let [v (matrix [1 2])
          m (matrix [[2 0] [0 2]])] 
      (transform! m v)
      (is (= (v/of 2 4) v)))))

(deftest test-slices
  (testing "slice row and column from matrix"
    (is (equals [1 2] (first (slices (matrix [[1 2] [3 4]])))))
    (is (equals [3 4] (second (slices (matrix [[1 2] [3 4]])))))
    (is (equals [3 4] (slice (matrix [[1 2] [3 4]]) 0 1)))
    (is (equals [2 4] (slice (matrix [[1 2] [3 4]]) 1 1))))
  (testing "slices of vector"
    (is (equals '(1.0 2.0 3.0) (slices (matrix [1 2 3])))))) 

;; verify scalar operators should still work on numbers!
(deftest test-scalar-operators
  (testing "addition"
    (is (== 2.0 (+ 1.0 1.0)))
    (is (== 3 (+ 1 2))))
  (testing "multiplication"
    (is (== 2.0 (* 4 0.5)))
    (is (== 6 (* 1 2 3))))
  (testing "subtraction"
    (is (== 2.0 (- 4 2.0)))
    (is (== 6 (- 10 2 2))))) 

(deftest test-compare
  (testing "eif"
    (is (= (eif (array [1 0 0]) (array [1 2 3]) (array [4 5 6])) (array [1 5 6]))))
  (testing "lt"
    (is (= (lt (array [0 2 -1 2]) 0) (array [0 0 1 0])))
    (is (= (lt (array [0 2 -1 2]) (array [1 2 3 4])) (array [1 0 1 1]))))
  (testing "le"
    (is (= (le (array [0 2 -1 2]) 0) (array [1 0 1 0])))
    (is (= (le (array [0 2 -1 2]) (array [1 2 3 4])) (array [1 1 1 1]))))
  (testing "gt"
    (is (= (gt (array [-1 2 0 4]) 0) (array [0 1 0 1])))
    (is (= (gt (array [-1 2 0 4]) (array [1 2 3 4])) (array [0 0 0 0]))))
  (testing "ge"
    (is (= (ge (array [-1 2 0 4]) 0) (array [0 1 1 1])))
    (is (= (ge (array [-1 2 0 4]) (array [1 2 3 4])) (array [0 1 0 1]))))
  (testing "ne"
    (is (= (ne (array [-1 2 0 4]) 0) (array [1 1 0 1])))
    (is (= (ne (array [-1 2 0 4]) (array [1 2 3 4])) (array [1 0 1 0]))))
  (testing "eq"
    (is (= (eq (array [-1 2 0 4]) 0) (array [0 0 1 0])))
    (is (= (eq (array [-1 2 0 4]) (array [1 2 3 4])) (array [0 1 0 1])))))

(deftest test-construction
  (testing "1D"
    (is (= (v/of 1.0) (matrix [1])))
    (is (instance? AVector (matrix [1]))))
  (testing "2D"
    (is (= (m/matrix [[1 2] [3 4]]) (matrix [[1 2] [3 4]])))
    (is (instance? AMatrix (matrix [[1]])))))

(deftest test-conversion
  (testing "vector" 
    (is (= [1.0] (to-nested-vectors (v/of 1.0))))
    (is (= [1.0] (coerce [] (v/of 1.0)))))
  (testing "matrix" 
    (is (= [[1.0]] (to-nested-vectors (m/matrix [[1.0]])))))
  (testing "coercion"
    (is (equals [[1 2] [3 4]] (coerce (m/matrix [[1.0]]) [[1 2] [3 4]])))
    (is (number? (coerce :vectorz 10)))
    (is (instance? AVector (coerce :vectorz [1 2 3])))
    (is (instance? AMatrix (coerce :vectorz [[1 2] [3 4]])))))

(deftest test-functional-ops
  (testing "eseq"
    (is (= [1.0 2.0 3.0 4.0] (eseq (matrix [[1 2] [3 4]]))))
    (is (empty? (eseq (coerce :vectorz []))))  
    (is (= [10.0] (eseq (array :vectorz 10))))  
    (is (= [10.0] (eseq (array :vectorz [[[10]]]))))  
    (is (== 1 (first (eseq (v/of 1 2))))))
  (testing "emap"
    (is (equals [1 2] (emap inc (v/of 0 1))))
    (is (equals [1 3] (emap + (v/of 0 1) [1 2])))
    ;; (is (equals [2 3] (emap + (v/of 0 1) 2))) shouldn't work - no broadcast support in emap?
    (is (equals [3 6] (emap + (v/of 0 1) [1 2] (v/of 2 3)))))
  (testing "long args"
;; TODO: fix in core.matrix 0.15.0
;    (is (equals [10] (emap + 
;                           (v/of 1) 
;                           [2] 
;                           (array :vectorz [3]) 
;                           (broadcast 4 [1]))))
    (is (equals [10] (emap + (array :vectorz [1]) [2] [3] [4])))
    (is (equals 10 (ereduce + (array :vectorz [[1 2] [3 4]]))))))

(deftest test-compute-array
  (is (equals [[0 1] [1 2]] (compute-matrix :vectorz [2 2] +)))
  (is (equals [[[0 1] [1 2]][[1 2][2 3]]] (compute-matrix [2 2 2] +))))

(deftest test-maths-functions
  (testing "abs"
    (is (equals [1 2 3] (abs [-1 2 -3])))
    (is (equals [1 2 3] (abs (v/of -1 2 -3)))))) 

(deftest test-assign
  (is (e== [2 2] (assign (v/of 1 2) 2)))
  (let [m (array :vectorz [1 2 3 4 5 6])]
    (is (e== [1 2 3] (subvector m 0 3)))
    (is (e== [4 5 6] (subvector m 3 3)))
    (assign! (subvector m 0 3) (subvector m 3 3))
    (is (e== [4 5 6 4 5 6] m)))
  (testing "mutable assign"
    (let [a (array [[1 2] [3 4]])]
      (assign! a [0 1])
      (is (equals [[0 1] [0 1]] a))))) 

;; vectorz operations hould return a vectorz datatype
(deftest test-vectorz-results
  (is (v/vectorz? (+ (v/of 1 2) [1 2])))
  (is (v/vectorz? (+ (v/of 1 2) 1)))
  (is (v/vectorz? (- 2 (v/of 1 2))))
  (is (v/vectorz? (* (v/of 1 2) 2.0)))
  (is (v/vectorz? (emap inc (v/of 1 2))))
  (is (v/vectorz? (array [[[1]]])))
  (is (v/vectorz? (to-vector (array [[[1]]]))))
  (is (v/vectorz? (identity-matrix 3)))
  (is (v/vectorz? (reshape (identity-matrix 3) [5 1])))
  (is (v/vectorz? (slice (identity-matrix 3) 1)))
  (is (v/vectorz? (* (identity-matrix 3) [1 2 3])))
  (is (v/vectorz? (inner-product (v/of 1 2) [1 2])))
  (is (v/vectorz? (outer-product (v/of 1 2) [1 2])))
  (is (v/vectorz? (add! (Scalar. 1.0) 10)))) 

(deftest test-shift 
  (is (v/vectorz? (shift (v/of 1 2) [1])))
  (is (equals [2 0] (shift (v/of 1 2) [1]))))

(deftest test-defensive-copy-on-double-array 
  (let [a (double-array [1 2 3 4 5])
        v (array a)]
    (aset-double a 4 9999)
    (is (equals v [1 2 3 4 5]))))

(deftest test-validate-shape
  (is (equals [2] (mp/validate-shape (v/of 1 2)))))

(deftest test-add-inner-product!
  (let [m (array :vectorz [1 2])
        a (array :vectorz [[0 2] [1 0]])
        b (array :vectorz [10 100])]
    (add-inner-product! m a b)
    (is (equals [201 12] m))
    (add-inner-product! m a b -1)
    (is (equals [1 2] m)))
  (is (equals [101 102] (add-inner-product! (array :vectorz [1 2]) 10 10)))
  (is (equals [101 102] (add-inner-product! (array :vectorz [1 2]) [1 2 3] [1 3 1] 10))))

(deftest test-add-outer-product!
  (let [m (array :vectorz [[1 2] [3 4]])
        a (array :vectorz [10 100])
        b (array :vectorz [7 9])]
    (add-outer-product! m a b)
    (is (equals [[71 92] [703 904]] m))
    (add-outer-product! m a b -1)
    (is (equals [[1 2] [3 4]] m)))
  (is (equals [11 32] (add-outer-product! (array :vectorz [1 2]) [1 3] 10)))
  (is (equals [11 32] (add-outer-product! (array :vectorz [1 2]) 10 [1 3])))
  (is (equals [101 302] (add-outer-product! (array :vectorz [1 2]) 10 [1 3] 10))))

(deftest test-logistic
  (is (equals [0 0.5 1] (logistic (array :vectorz [-1000 0 1000])))))

(deftest test-select-regression
  (let [m (new-matrix 3 4)
        col (select m :all 1)]
    (assign! col [3 4 5])
    (is (equals [[0 3 0 0] [0 4 0 0] [0 5 0 0]] m))))

(deftest test-array-add-product-regression
  (let [a (array :vectorz [[[1]]])]
    (is (equals [[[61]]] (add-scaled-product a [2] [3] 10)))
    (is (equals [[[1]]] a)))
  (let [a (array :vectorz [[[1]]])]
    (is (equals [[[61]]] (add-scaled-product! a [2] [3] 10)))
    (is (equals [[[61]]] a))))

;; regression test for #54
(deftest test-diagonal-inverse-regression
  (is (equals (inverse [[1 0] [0 2]]) (inverse (diagonal-matrix [1 2])))))

;; run compliance tests

(deftest instance-tests
  (clojure.core.matrix.compliance-tester/instance-test (Scalar. 2.0))
  (clojure.core.matrix.compliance-tester/instance-test (v/of 1 2))
  (clojure.core.matrix.compliance-tester/instance-test (v/of 1 2 3))
  (clojure.core.matrix.compliance-tester/instance-test (v/of 1 2 3 4 5 6 7))
  (clojure.core.matrix.compliance-tester/instance-test (subvector (v/of 1 2 3 4 5 6 7) 2 3))
  (clojure.core.matrix.compliance-tester/instance-test (matrix :vectorz [[[1 2] [3 4]] [[5 6] [7 8]]]))
  (clojure.core.matrix.compliance-tester/instance-test (clone (first (slices (v/of 1 2 3)))))
  (clojure.core.matrix.compliance-tester/instance-test (first (slices (v/of 1 2 3))))
;;  (clojure.core.matrix.compliance-tester/instance-test (Vector/of (double-array 0))) ;; TODO: needs fixed compliance tests
  (clojure.core.matrix.compliance-tester/instance-test (first (slices (v/of 1 2 3 4 5 6))))
  (clojure.core.matrix.compliance-tester/instance-test (array :vectorz [[1 2] [3 4]]))
  (clojure.core.matrix.compliance-tester/instance-test (array :vectorz [[[[4]]]]))
  (clojure.core.matrix.compliance-tester/instance-test (Array/create (array :vectorz [[[[4 3]]]])))
  (clojure.core.matrix.compliance-tester/instance-test (Index/of (int-array [1 2 3])))) 

(deftest compliance-test
  (clojure.core.matrix.compliance-tester/compliance-test (v/of 1 2))) 
