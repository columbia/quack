<?php

/* Main purpose for this test - test unserialize in a non-standard assignement expressions */

/* Type inference tools SHOULD at least get the type of this right */
$test_data = new GoodClass();
$test_data->hello();

$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';

// joomla-3.0.2/libraries/simplepie/simplepie.php, LINE#=[9020]
$feed['child'][SIMPLEPIE_NAMESPACE_ATOM_10]['entry'][] = unserialize($object);

// joomla-3.0.2/libraries/joomla/input/cli.php, LINE#=[97]
list($arg1, $arg2) = unserialize($object);
print($arg1);
print($arg2);

$x = unserialize($object);
$x === true;

$outdata = @unserialize($object); // POI bug
print($outdata);

// joomla-3.0.2/components/com_finder/models/search.php, LINE#=[129]
$a = array();
$b = unserialize($object);
$b->weight = $a;
$a[$rk] = $b;

// bug from: FILE=[/var/local/de-ser-defense/PHP/projects/cubecart-5.2.0/classes/cart.class.php], LINE#=[899]
$countries = (1==0) ? unserialize($object) : false;
print($countries)

// just trying out the last form of assignement
$countries2 = "a string";
$countries2 .= unserialize($object); // this should result in the unser deduced as string (concat)

function fake(){
    $object2 = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
    return unserialize($object2);
}

function test_cond_ret($obj, $arg) {
    // FILE[typo3/typo3/sysext/core/Classes/Cache/Frontend/VariableFrontend.php], LINE#=[85]
    return $obj instanceof GoodClass ? $arg : unserialize($arg);
}

function dummy($arg) {
    return $arg;
}

// FILE typo3/typo3/sysext/core/Classes/TypoScript/Parser/TypoScriptParser.php, LINE#=[480]
$outdata2 = dummy(unserialize($object));
print($outdata2);

// FILE typo3/typo3/sysext/core/Classes/Database/QueryGenerator.php, LINE#=[1465]
$outdata3 = dummy((1==0) ? unserialize($object) : '');
print($outdata3);

function typeddummy(String $arg) {
    return $arg;
}

// FILE typo3/typo3/sysext/core/Classes/TypoScript/Parser/TypoScriptParser.php, LINE#=[480]
$outdata4 = typeddummy(unserialize($object));

// FILE typo3/typo3/sysext/install/Classes/Updates/SeparateSysHistoryFromSysLogUpdate.php, LINE#=[316]
$outdata5 = (array) unserialize($object);
var_dump($outdata5);

// FILE typo3/typo3/sysext/core/Classes/Registry.php, LINE#=[172]
function test_array_entry_key($row) {
    $entries[$row['entry_key']] = unserialize($row['entry_value']);
    print($entries[$row['entry_key']]);
}

// FILE typo3/sysext/core/Tests/Functional/RegistryTest.php, LINE#=[]
function test_as_arg_in_func($object) {
    dummy(unserialize($object));
}
