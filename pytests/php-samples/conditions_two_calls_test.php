<?php

/* Main purpose for this test - capture the method-call condition */

$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$data = unserialize($object); // POI bug
//
// this is currently ignored by our analysis
//
$data->hey();
//
// this is currently caught by our analysis
//
$data->ola()->to_you_to();

?>
