<?php

require 'vendor/autoload.php';

use MyNamespace\MyClass;
use MyNamespace\AnotherClass;
use MyNamespace\NestedNamespace\MyOtherClass;

$myClass = new MyClass();
$myClass->sayHello();

$anotherClass = new AnotherClass();
$anotherClass->sayHello();

$myOtherClass = new MyOtherClass();
$myOtherClass->sayHello();

$object = "";
$object = unserialize($object);
$object->sayHello();

