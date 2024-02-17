<?php

/* Main purpose for this test - unserialize in class
    (complement to "fugio inspired" testing funcs and files) */

class Cart {
    public function add($obj){
        $countries = (1==0) ? unserialize($obj['asd']) : false;
    }
}