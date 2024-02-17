<?php

/* Main purpose for this test - working with files/functions in the right scope */

/* Type inference tools SHOULD at least get the type of this right */
$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';

$data = unserialize($object); // POI bug
print "object:   " . gettype($data) . "\n";
print "class:    " . get_class($data) . "\n";
print "basename: " . basename(get_class($data)) . "\n";

/* This will still cause a runtime error after deserialization
 * because the attacker created a Logger instance which doesn't
 * contain a 'hello()' method, but the attack still succeeds because
 * Logger's __wakeup is called during deserialization. */
$data->hello(); // Use a (GoodClass) class method for type inference.

/* If this was a call to a Logger method instead, I don't think
 * having type information would help us prevent the attack, since
 * the attacker can still create a (valid) Logger object. */
/* $data->log->save(); // Use a Logger method for type inference. */

?>
