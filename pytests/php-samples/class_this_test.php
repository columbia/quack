<?php
class Person {
    public $varValue;

    function __construct( $name ) {
        $this->varValue = deserialize($name);

        foreach ($GLOBALS['load_callback'] as $callback)
            {
                if (is_array($callback))
                {
                    $this->import($callback[0]);
                    $this->varValue = $this->$callback[0]->$callback[1]($this->varValue, $this);
                }
                elseif (is_callable($callback))
                {
                    $this->varValue = $callback($this->varValue, $this);
                }
            }
    }
};