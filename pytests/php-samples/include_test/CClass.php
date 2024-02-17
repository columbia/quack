<?php

require "D1Class.php";
require "D2Class.php";

class CClass {
    public function foo() : void {
        print("Hello from C::foo()\n");
    }

    public function bar() : void {

        $a1 = new A1Class();
        $a1->foo();
        $a2 = new A2Class();
        $a2->foo();
        $b = new BClass();
        $b->foo();
        $c = new CClass();
        $c->foo();
        $d1 = new D1Class();
        $d1->foo();
        $d2 = new D2Class();
        $d2->foo();
        $e = new EClass();
        $e->foo();
        $f = new FClass();
        $f->foo();

        # A, B, C, D, E and F should be available
        $object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
        unserialize($object);

    }
}


?>
