<?php

class LoneClass {
    public function lone_call() {
        return "foo";
    }
}

class SecondClass {
    public function second_call() {
        return "bar";
    }

}

class FirstClass {
    public function first_call() {
        $second_class_obj = new SecondClass();

        return $second_class_obj;
    }
}




/* Main purpose for this test - capture the method-call condition */

$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$data = unserialize($object); // POI bug
$data->lone_call();


$data2 = unserialize($object); // POI bug
$data2->first_call()->second_call();

?>
