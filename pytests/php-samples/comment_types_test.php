<?php

/**
* @return bool|HTMLPurifier_Config
*/
function just_a_func(){
    $now = new DateTime('now', new DateTimeZone('Asia/Taipei'));
    return unserialize(serialize($now));
}

var_dump(just_a_func());
