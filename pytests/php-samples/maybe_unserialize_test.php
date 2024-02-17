<?php

/* Test if our analysis also recognizes calls to maybe_unserialize (Wordpress function),
 * even if we don't have Wordpress installed */
$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$object = maybe_unserialize($object);
print($object);

?>
