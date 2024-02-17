<?php

require_once "./namespace1.php";
require_once "./namespace2.php";

use function Namespace2\namespaced_func;

$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$var = unserialize($object);
namespaced_func($var);

?>
