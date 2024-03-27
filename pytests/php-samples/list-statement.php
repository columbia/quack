<?php

class FooClass
{
    public function foo() {
        return 'foo';
    }
}

class BarClass
{
    public function bar() {
        return 'bar';
    }
}

$in_object = "SOMESTRING";

# For reference
$var = unserialize($in_object);
$var[0]->foo();
$var[1]->foo();

list($arg1, $arg2) = unserialize($in_object);
$arg1->foo();
$arg2->foo();

$var2 = unserialize($in_object);
list($arg3, $arg4) = $var2;
$arg3->bar();
$arg4->bar();

list($arg5, $arg6) = unserialize($in_object);
$arg5->foo();
$arg6->bar();
