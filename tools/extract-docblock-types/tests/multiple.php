<?php

/**
 * @param int $a
 * @return int
 */
function test($a)
{
    return $a + 10;
}

/**
 * @param string $b
 * @return string
 */
function foo($b)
{
    return $b . "world";
}

function bar($c)
{
    return $c + test($c);
}
