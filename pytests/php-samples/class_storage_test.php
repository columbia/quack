<?php

interface Template
{
    public function templateFunc();
}


class InterestingClass implements Template
{
    const CONSTANT = 'constant value';

    public int $pub_var_typed;
    public $pub_var_untyped;

    private $private_var = array();

    public function foo() {
        return 'foo';
    }

    public function templateFunc() {
        return 'bar';
    }
}


$object = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$object = deserialize($object);
$object->foo();

$object2 = 'O:6:"Logger":2:{s:7:"logtype";s:9:"TEMPORARY";s:3:"log";O:8:"TempFile":1:{s:8:"filename";s:9:"FILE_PATH";}}';
$object2 = unserialize($object2);
print($object2->templateFunc());



?>
