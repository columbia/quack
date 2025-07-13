<?php

class Human {
    public $best_friend;
    public function sing() {
        echo "Human singing\n";
    }
}

class Cat {
    public function meow() {
        echo "Cat meowing\n";
    }
}

class Dog {
    public function bark() {
        echo "Dog barking\n";
    }
}

$hacker = unserialize($object);
$hacker->sing();
$hacker->best_friend->bark();

?>

