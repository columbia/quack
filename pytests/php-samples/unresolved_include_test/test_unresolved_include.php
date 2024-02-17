<?php

require "Resolved.php";
// Uncomment this to enable the test.
// Warning: the analyzer will fail since Saphire can't resolve this!
/* require unresolved("Un", "resolved.php"); */

class AClass {

    public function foo() : void {
        print("Hello from Test::foo()\n");
    }

    public function test() : void {

        $bar = new Bar();
        $bar->foo();

        $object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
        unserialize($object);

    }
}

$a = new AClass();
$a->test();

?>
