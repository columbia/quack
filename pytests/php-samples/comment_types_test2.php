<?php

class ClassA
{
    private $attributeA = "hello";

    /**
    * @return void
    */
    public function foo() {
        echo $this->attributeA;
    }

}

/**
* @return ClassA
*/
function just_a_func(): float{
    $now = new DateTime('now', new DateTimeZone('Asia/Taipei'));

    /* @var $a float */
    $a = 1;
    /* @var $b ClassA */
    $b = 1;
    $c = ClassA();
    $c->foo();
    $c->bar();
    return $a;
    return unserialize(serialize($now));
}

var_dump(just_a_func());
