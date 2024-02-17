<?php

require "BClass.php";
require "FClass.php";

class A2Class {

    public function foo() : void {
        print("Hello from A2::foo()\n");
    }
}

$c = new CClass();
$c->bar();

?>
