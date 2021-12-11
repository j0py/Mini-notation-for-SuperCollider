# Tidal-syntax-for-SClang
Use Tidal syntax to play with NodeProxy objects.

## The idea

When exploring Tidal i found the short syntax quite nice to use.

This project tries to create that in SClang.

The basic idea is this:

```
NP(~a).snd("bd:2 hh:1 cl:1 sn:7").play(0.7).mon(0.5);
```

The NP class is given a NodeProxy, and has (possibly many) chained methods.  
These methods will operate on the NodeProxy.

NP will initialize the NodeProxy with:
```
proxy.ar(2);
proxy.source = { Silent.ar()!2 };
```

The ```snd()``` method is given a specification what sound(s) to play.  
This specification is parsed and then stored in the NP class.
The specification above specifies 4 steps in a sequence.  
The first step should play a "bd" sound.  
If there is a map ```samples/bd``` available, then the third sample file found there will be played (using a built-in Synthdef which can play a sample file).  
If ```samples/bd``` is not available, then a Synthdef added as ```\bd``` will be played.

The structure / timing for the Pbind can also be derived from the given specification. The specification above shows us 4 steps (notes) to play, so the resulting durations should be ```[1/4, 1/4, 1/4, 1/4]``` used in a Pseq in the Pbind.

More information about this "mini-notation" can be found in various Tidal tutorials or documentation. The NPParser class (in np_mininotation.sc) takes care of parsing the specifications in mini-notation format.

The ```play()``` method will create a Pbind on the NodeProxy, using the information that has been stored inside the NP object by other methods that were called before ```play()``` was called.

The parameter to ```play()``` is the volume with which the Pbind should play on the NodeProxy private bus.

The default slot index where the Pbind is added is slot 10, but you may specify another slot number. This way you could play more than 1 Pbind at the same time on one NodeProxy.

The ```mon()``` method will call ```play``` on the NodeProxy, and the parameter to ```mon()``` is the monitor volume to use. You could also play the NodeProxy directly, instead of through the NP class (```~a.vol_(0.5).play```).

The mininotation parser supports nested steps using ```[ ]``` and alternating steps using ```< >```, supplying numbers (notes) with a colon ```bd:3``` and more. At the bottom of this README i keep track of what has been realized when.

The snd() method specifies what sound is played (sample or synthdef), and it may also specify the numbers (sample number or midinote number).  
But you can also use the num() method to specify the numbers separately.

The param() method lets you specify values for any other parameter for the played synthdef (np_playbuf or one of your own synthdefs).

With all these methods you can specify the data in mininotation format.

In Tidal you can say which of the given specifications finally determines the 'structure', that means the durations for the Pbind. In NP i will make two versions for each method: one regular, and one postfixed with an ```_``` character. If you call ```snd_()``` then the given specification for the sound will be used to create the structure for Pbind. The other specified data will "wrap along" in the Pbind.

The rule will be that the _first_ called method that takes a specification will determine the structure for the Pbind. But the _last_ called ```xxx_``` method that takes a specification will override that.

So far the description and ideas for this project.

## Using it in SuperCollider

I use ```git clone``` to get the sources somewhere on my disk.

Then i go inside the ```~/.local/share/SuperCollider/Extensions``` folder, and i place there a _symbolic link_ to the folder where the sources are. Like this: ```ln -s ~/repos/Tidal-syntax-for-SClang np```.

Then start SuperCollider, recompile the classes and the NP class should be available. It appears to be that SuperCollider will follow symbolic links.

The NP class uses the Samples class to play samples.  
Before using the NP class, you should call ```Samples.load(<path>, <ext>)``` to load your samples. You specify the ```<path>``` relative to the file where the code that you run is in. With ```<ext>``` you can let Samples find all ".wav" or ".aiff" files. But not both at the same time, yet.

One thing i need to do is give the NP class proper documentation so that SuperCollider will display the documentation in its class browser. Will figure that out.

## Small example

Create a ```.scd``` file with this code in it:  
(i assume a "samples" folder with "bd", "sn" etc subfolders with samples)

```
s.boot;

Samples.load("samples", "wav");

p = ProxySpace.push(s).makeTempoClock(100/60).quant_(2);

NP(~a).snd("bd:2 [sn:1 sn:1] ~ bd:2").play(0.5).mon(0.3);

NP(~b).snd("~ ~ [hh hh hh] ~").play(0.5).mon(0.3);

p.clock.tempo_(60/60)
```

Load it in SuperCollider and evaluate.

## Example setting the structure and using amps

```
s.boot;

Samples.load("samples", "wav");

p = ProxySpace.push(s).makeTempoClock(60/60).quant_(2);

NP(~a).snd("bd <~ [~ bd:2]> ~ ~ sn ~ ~ ~").play(0.5).mon(0.3);

NP(~b).snd("~ ~ hh hh").param_(\amp, "0.2 0.8 0.2").play(0.5).mon(0.4);

NP(~c).snd("rd").num_("1 1 1 3").play(0.5).mon(0.1);
```

## More ideas

```bin("10001010")```

```bin``` could be used to declare the structure, thereby overriding all other structure defining method calls.

```hex("92")```

Just like ```bin``` you can think of much shorter hexadecimal notation. ```"92"``` in hex equals binary ```"10010010"```.

## Tidal syntax not supported yet:

```,``` paralell running steps

```!``` repeats the last step, e.g.: ```"bd bd bd" = "bd bd !" = "bd!3"```

```*``` ```"bd sn*2" = "bd [sn sn]"``` plays 2 snares in same step, so speeds up

```/``` ```"bd sn/2" = "bd <sn ~>"``` plays 1/2 snare in one step, next cycle
        it plays the other half of the snare, which yields silence.
        slows down

```@``` ```[bd sn@0.5]``` makes duration of the snare half as long

```bd(3,8)``` euclidian rhythms ( ```bd:2(3,8)``` is also possible )

## Latest developments

20211128: parsing */@ works (stretching), now only have use it during play..  
20211201: ~ in notes is also a \rest; shortened Pbind; 
20211210: using pbindf, add any other param for your synthdef:  

```
(
NP(~rhythm)
.num("2 0 [1 5] <8 ~ [5 4] 2>")
.snd("potsandpans")
.param(\amp, "0.3 0.2 1 0.3 0.6")
.param(\pan, "-1 0 0 0 1")
.param(\spread, "0")
.play(0.3)
.mon(0.5);
)
```
20211211: the */@ (stretching) works for value steps now  
```
NP(~a).snd("bd <~ [~ bd:2/1.5]> ~ ~ sn*3/2 ~ ~ ~").play(0.5).mon(0.3);
```
next is to make stretching wotk for ```[]``` and ```<>``` too.


