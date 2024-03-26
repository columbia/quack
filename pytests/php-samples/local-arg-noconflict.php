<?php

function assign($var, $notvalue=null)
{
    foreach ($var as $key => $value)
    {
       print($key . " => " . $value);
    }
}

$var = deserialize("SOMESTRING");
assign($var);