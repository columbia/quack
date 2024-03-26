<?php

$some_str = "hello";

// First element elvis
$first_elem = deserialize("some input");
$result =  $first_elem ?: $some_str;

// Second element elvis
$second_elem = deserialize("some input");
$result2 =  $some_str ?: $second_elem;

// Both element elvis (TODO make this work - shouldn't result 'deserialize.<returnValue>'?)
$both_elem = deserialize("some input");
$result3 =  $both_elem ?: $both_elem;

$other_str = "world";

// First element ternary (TODO make this work - shouldn't result string)
$first_elem_t = deserialize("some input");
$result4 =  $first_elem_t ? $other_str : $some_str;

// Second element ternary
$second_elem_t = deserialize("some input");
$result5 =  $other_str ? $second_elem_t : $some_str;

// Third element ternary
$third_elem_t = deserialize("some input");
$result6 =  $other_str ? $some_str : $third_elem_t;

// All element ternary (TODO make this work - shouldn't result 'deserialize.<returnValue>')
$all_elem_t = deserialize("some input");
$result7 =  $all_elem_t ? $all_elem_t : $all_elem_t;

