<?php

class ClassA
{
    private $attributeA = "hello";

    public function foo() {
        echo $this->attributeA;
    }

}
class ClassB
{
    private $attributeA = "hello";

    public function foo() {
        echo $this->attributeA;
    }

}

/**
 * @return ClassA
 */
function my_func($blob) {
    $obj = unserialize($blob);
    return $obj;
}
