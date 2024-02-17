<?php

/* Test: inconsistency between Docblock return type and the instantiated object
 * in the return expression.
 *
 * In this test, the Docblock indicates that the my_func() function has return
 * type of ClassA, but return statement in my_func() instantiates and returns
 * an object of ClassB.
 *
 * A good type inferrence tool should indicate the inconsistency.
 *
 * Psalm does not issue any warnings about this test.
 * */

class ClassA
{
    private $attributeA = "hello";

    public function foo() {
        echo $this->attributeA;
    }

}

class ClassB
{
    public $attributeB = 42;

    public function bar() {
        echo $this->attributeB;
    }
}

/**
 * @return ClassA
 */
function my_func() {
    return new ClassB();
}

my_func();
