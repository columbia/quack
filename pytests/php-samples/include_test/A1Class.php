<?php

require "BClass.php";
require "FClass.php";

class A1Class {

    public function foo() : void {
        print("Hello from A1::foo()\n");
    }
}

$c = new CClass();
$c->bar();

?>
