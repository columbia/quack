<?php

include "src" . "/Literal" . ".php";
include __DIR__ . "/src/Magic.php";
include dirname(__FILE__) . "/src/Builtin.php";
include "src" . unknown_function("blahblah") . "/SomeClass.php";

$object = "";
unserialize($object);

?>
