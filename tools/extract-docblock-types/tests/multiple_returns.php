<?php

/**
 * @return ClassA|ClassB
 */
function my_func($blob) {
    $obj = unserialize($blob);
    $obj->foo();
    return $obj;
}
