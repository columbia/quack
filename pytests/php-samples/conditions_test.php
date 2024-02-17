<?php

/* Main purpose for this test - capture the used-as-arg condition (record it was arg #1) */

/* Type inference tools SHOULD at least get the type of this right */
$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';

$data = unserialize($object); // POI bug
//
// should report as condition funccall, substr
//
print(substr($data, 1));
//
// should be reported as condition array-deref  (this is "mixed-var-array-access")
//
$something = $data['geti'];
//
// should be reported as condition array-deref  (this is "???")
//
$something = $data[0]

?>
