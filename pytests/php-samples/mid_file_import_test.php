<?php

/* Main purpose for this test - check our class available at locaiton  */

$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$outdata = unserialize($object); // POI bug

function fake(){
    $object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';

    require 'analysis_tests_helpers/GoodClass.php';
    require 'analysis_tests_helpers/Logger.php';
    require 'analysis_tests_helpers/Stream.php';

    $outdata = unserialize($object); // this should work
}

$outdata = unserialize($object); // POI bug

fake();

?>
