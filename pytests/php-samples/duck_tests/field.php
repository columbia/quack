<?php

class Duck {
    public $feather_color;
}

class Whale {
    public $flippers;
}

$animal = unserialize($object);
echo "This duck's feathers are $animal->feather_color";

?>
