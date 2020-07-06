# IMCW - Import Minecraft World from extracted JSON

## Introduction

This tool which is written in Clojure (which compiles down into Java Virtual Machine as standard JAR files).

It takes the extracted JSON and pre

IMCW is a tool for processing the extracted blocks.  It is the interface to the RITA agent, and could be a connection to the testbed.  I separated this out to produce an output file, but our internal version publishes the data to our bus.  We could therefore easily make a version that publishes to the MMQT message bus.

The tool is written in Clojure, which compiles out to Java JAR files.  I have included the binary in the repo so that you don't have to install clojure in order to use it.
Clojure is an open source platform and can be easily installed (https://clojure.org/guides/getting_started).

The tool does two things.

1. It finds all of the doors (various construction materials used). Doors are interesting because they take up multiple blocks.  This tool collects the blocks that constitute a door and provides a single door object with top-left and bottom-right cooordinates.

2. It removes all of the "air" blocks that represent most of the space in the building, thus dramatically reducing the size of the resulting JSON file.

3. For our use, it also extracts all of the switches (levers) and the victims (prismarine and gold_block) These are available using the -l and -v commandline options.

## Running the tool

The binary (imcw.jar) is in the "target directory under Import-MC-World" and can be invoked like this...

➜  Import-MC-World git:(master) ✗ java -jar target/imcw.jar --help

Generate the minecraft-world: null

imcw

Usage: imcw [options] action

Options:
  -o, --output file     mcworld.json  output
  -i, --import file     false         Import from extracted json
  -d, --dont-dehydrate                Don't remove air
  -m, --map                           Print the map
  -l, --levers                        find all levers (switches)
  -v, --victims                       find all victims
  -h, --help

Actions:
  import

The import file is the JSON file extracted from the region files. and the output file is another JSON format that provides the doors, and optionally the victims and switches and also the data containing all of the data except the "air".

Here is an example run:

➜  Import-MC-World git:(master) ✗ java -jar target/imcw.jar -i blocks_in_building_2yang.json -o demo.json import

And here is a snippet of the generated JSON showing a few doors and a few data items.  The output is pretty printed here for readibility.  The file itself is not pretty printed.


{
    "doors": [
        [
            [
                -2111,
                53,
                178
            ],
            [
                -2111,
                52,
                178
            ]
        ],
        [
            [
                -2113,
                53,
                194
            ],
            [
                -2113,
                52,
                194
            ]
        ],
        ...
        ],
    "data": [
        [
            [
                -2150,
                52,
                169
            ],
            "stained_hardened_clay"
        ],
        ...
        [
            [
                -2126,
                53,
                164
            ],
            "quartz_stairs"
        ],
        [
            [
                -2142,
                52,
                167
            ],
            "end_portal_frame"
        ],
        ...
      ]
}

## Integration thoughts,

You could extract the world data using the ASIST-MC-toolkit (https://github.com/zt-yang/ASIST-MC-toolbox) and then run this tool to generate a JSON file to be imported.
Alternatively we could make this tool publish its output directly to the MQTT bus,, which we would be happy to do.
