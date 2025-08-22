<?php

class Duck {
    public function swim() {
        echo "Duck swimming\n";
    }

    public function fly() {
        echo "Duck flying\n";
    }
}

class Whale {
    public function swim() {
        echo "Whale swimming\n";
    }
}

function test_duck($object) {
    $animal = unserialize($object);
    $animal->swim();
    $animal->fly();
}

?>
