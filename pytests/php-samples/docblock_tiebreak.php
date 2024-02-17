<?php

/* Test: using the Docblock return type to break ties between possible return
 * type objects to identify the correct one.
 *
 * In this test, the Docblock indicates that the my_func() function can return
 * objects of either ClassA or ClassB. The deserialized object (which is
 * returned) has a foo() method call, which only ClassA has. Therefore, the
 * return object should only be ClassA. Further, ClassC also has a foo()
 * method, but because it is not in the Dockblock annotation, it is not
 * considered.
 *
 * A good type inferrence tool should indicate that the return type of my_func
 * is ClassA (and not ClassB or ClassC).
 *
 * Psalm reports that the return type of my_func is ClassA|ClassB.
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

class ClassC
{
    public $attributeC = null;

    public function foo() {
        echo $this->attributeC;
    }
}

/**
 * @return ClassA|ClassB
 */
function my_func($blob) {
    $obj = unserialize($blob);
    $obj->foo();
    return $obj;
}
