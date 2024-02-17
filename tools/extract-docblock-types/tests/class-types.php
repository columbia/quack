<?php

class ClassA {

    /**
     * @param int $a
     * @param int $b
     * @return int
     */
    function foo($a, $b) {
        return $a + $b;
    }
}

/**
 * @param ClassA $classA
 * @return int
 */
function bar($classA) {
    return $classA.foo(1, 2);
}
