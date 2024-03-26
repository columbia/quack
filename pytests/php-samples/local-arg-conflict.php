<?php

function assign($var, $value=null)
{
    foreach ($var as $key => $value)
    {
       print($key . " => " . $value);
    }
}

$var = deserialize("SOMESTRING");
assign($var);