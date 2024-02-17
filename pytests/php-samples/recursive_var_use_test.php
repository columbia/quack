<?php

$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$orig = unserialize($object);
$accum = array('some' => 'value');

foreach ($orig as $key => $value) {
    $accum[$key] = $value;
}

foreach ($accum as $another_key => $another_value) {
    print("Value: $value\n");
    print("another_value: $another_value\n");
}

$arr = array();
$var = unserialize($object);
array_push($arr, $var);
print($var . "\n");

$first = unserialize($object);
$second = $first + 1;
print($second);

?>
